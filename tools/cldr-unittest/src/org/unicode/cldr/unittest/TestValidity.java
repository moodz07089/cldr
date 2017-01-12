package org.unicode.cldr.unittest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.unicode.cldr.tool.ToolConstants;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.StandardCodes.LstrType;
import org.unicode.cldr.util.Validity;
import org.unicode.cldr.util.Validity.Status;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.ibm.icu.text.UnicodeSet;

public class TestValidity extends TestFmwkPlus {

    public static void main(String[] args) {
        new TestValidity().run(args);
    }

    Validity validity = Validity.getInstance(CLDRPaths.COMMON_DIRECTORY);

    public void TestBasicValidity() {
        Object[][] tests = {
            { LstrType.language, Validity.Status.regular, true, "aa", "en" },
            { LstrType.language, null, false, "eng" }, // null means never found under any status
            { LstrType.language, null, false, "root" },
            { LstrType.language, Validity.Status.special, true, "mul" },
            { LstrType.language, Validity.Status.deprecated, true, "aju" },
            { LstrType.language, Validity.Status.private_use, true, "qaa" },
            { LstrType.language, Validity.Status.unknown, true, "und" },

            { LstrType.script, Validity.Status.regular, true, "Zyyy" },
            { LstrType.script, Validity.Status.special, true, "Zsye" },
            { LstrType.script, Validity.Status.regular, true, "Zyyy" },
            { LstrType.script, Validity.Status.unknown, true, "Zzzz" },

            { LstrType.region, Validity.Status.deprecated, true, "QU" },
            { LstrType.region, Validity.Status.macroregion, true, "EU" },
            { LstrType.region, Validity.Status.regular, true, "XK" },
            { LstrType.region, Validity.Status.macroregion, true, "001" },
            { LstrType.region, Validity.Status.private_use, true, "AA" },
            { LstrType.region, Validity.Status.unknown, true, "ZZ" },

            { LstrType.subdivision, Validity.Status.unknown, true, "kzzzzz" },
            { LstrType.subdivision, Validity.Status.regular, true, "usca" },
            { LstrType.subdivision, Validity.Status.deprecated, true, "albr" },

            { LstrType.currency, Validity.Status.regular, true, "USD" },
            { LstrType.currency, Validity.Status.unknown, true, "XXX" },
            { LstrType.currency, Validity.Status.deprecated, true, "ADP" },

            { LstrType.unit, Validity.Status.regular, true, "area-acre"},
        };
        for (Object[] test : tests) {
            LstrType lstr = (LstrType) test[0];
            Validity.Status subtypeRaw = (Validity.Status) test[1];
            Boolean desired = (Boolean) test[2];
            for (int i = 3; i < test.length; ++i) {
                String code = (String) test[i];
                List<Status> subtypes = subtypeRaw == null ? Arrays.asList(Status.values()) : Collections.singletonList(subtypeRaw);
                for (Status subtype : subtypes) {
                    Set<String> actual = validity.getStatusToCodes(lstr).get(subtype);
                    assertRelation("Validity", desired, CldrUtility.ifNull(actual,Collections.EMPTY_SET), TestFmwkPlus.CONTAINS, code);
                }
            }
        }
        if (isVerbose()) {

            for (LstrType lstrType : LstrType.values()) { 
                logln(lstrType.toString());
                final Map<Status, Set<String>> statusToCodes = validity.getStatusToCodes(lstrType);
                for (Entry<Validity.Status, Set<String>> entry2 : statusToCodes.entrySet()) {
                    logln("\t" + entry2.getKey());
                    logln("\t\t" + entry2.getValue());
                }
            }
        }
    }

    static final Set<String> ALLOWED_UNDELETIONS = ImmutableSet.of("NL-BQ1", "NL-BQ2", "NL-BQ3", "NO-21", "NO-22");
    static final Set<String> ALLOWED_MISSING = ImmutableSet.of("root");

    public void TestCompatibility() {
        // Only run the rest in exhaustive mode, since it requires CLDR_ARCHIVE_DIRECTORY
        if (getInclusion() <= 5) {
            return;
        }

        final String oldCommon = CLDRPaths.ARCHIVE_DIRECTORY + "cldr-" + ToolConstants.PREVIOUS_CHART_VERSION + "/common/";
        Validity oldValidity = Validity.getInstance(oldCommon);
        for (LstrType type : LstrType.values()) { 
            final Map<Status, Set<String>> statusToCodes = validity.getStatusToCodes(type);
            if (statusToCodes == null) {
                logln("validity data unavailable: " + type);
                continue;
            }
            for (Entry<Status, Set<String>> e2 : statusToCodes.entrySet()) {
                Status oldStatus = e2.getKey();
                for (String code : e2.getValue()) {
                    Status newStatus = getNewStatus(type, code);
                    if (oldStatus == newStatus) {
                        continue;
                    }
                    if (oldStatus == Status.regular && newStatus != Status.deprecated) {
                        errln(type + ":" + code + ":" + oldStatus + " — regular item changed, and didn't become deprecated");
                    }
                    if (newStatus == null && !ALLOWED_MISSING.contains(code)) {
                        errln(type + ":" + code + ":" + oldStatus + " — missing in new data");
                    } 
                    if (oldStatus == Status.deprecated && !ALLOWED_UNDELETIONS.contains(code)) {
                        errln(type + ":" + code + ":" + oldStatus + " => " + newStatus 
                            + " // add to exception list if really un-deprecated");
                    } else {
                        logln(type + ":" + code + " was " + oldStatus + " => " + newStatus);
                    }
                }
            }
        }
    }

    private Status getNewStatus(LstrType type, String code) {
        Map<Status, Set<String>> info = validity.getStatusToCodes(type);
        for (Entry<Status, Set<String>> e : info.entrySet()) {
            if (e.getValue().contains(code)) {
                return e.getKey();
            }
        }
        return null;
    }

    public void TestBothDirections() {
        for (LstrType type : LstrType.values()) {
            Map<Status, Set<String>> statusToCodes = validity.getStatusToCodes(type);
            Map<String, Status> codeToStatus = validity.getCodeToStatus(type);
            assertEquals("null at same time", statusToCodes == null, codeToStatus == null);
            if (statusToCodes == null) {
                logln("validity data unavailable: " + type);
                continue;
            }
            for (Entry<Status, Set<String>> entry : statusToCodes.entrySet()) {
                Status status = entry.getKey();
                for (String code : entry.getValue()) {
                    assertEquals("Forward works", status, codeToStatus.get(code));
                }
            }
            for (Entry<String, Status> entry : codeToStatus.entrySet()) {
                final String code = entry.getKey();
                final Status status = entry.getValue();
                assertTrue("Reverse works: " + status, statusToCodes.get(status).contains(code));
            }
        }
    }

    public void TestUnits() {
        Splitter HYPHEN_SPLITTER = Splitter.on('-');
        UnicodeSet allowed = new UnicodeSet("[a-z0-9A-Z]").freeze();
        Validity validity = Validity.getInstance(CLDRPaths.COMMON_DIRECTORY);
        Map<String,String> shortened = ImmutableMap.<String,String>builder()
            .put("acceleration", "accel")
            .put("revolution", "revol")
            .put("centimeter", "cmeter")
            .put("kilometer", "kmeter")
            .put("milligram", "mgram")
            .put("deciliter", "dliter")
            .put("millimole", "mmole")
            .put("consumption", "consumpt")
            .put("100kilometers", "100km")
            .put("microsecond", "microsec")
            .put("millisecond", "millisec")
            .put("nanosecond", "nanosec")
            .put("milliampere", "milliamp")
            .put("foodcalorie", "foodcal")
            .put("kilocalorie", "kilocal")
            .put("kilojoule", "kjoule")
            .put("frequency", "freq")
            .put("gigahertz", "gigahertz")
            .put("kilohertz", "khertz")
            .put("megahertz", "megahertz")
            .put("astronomical", "astro")
            .put("decimeter", "dmeter")
            .put("micrometer", "micmeter")
            .put("scandinavian", "scand")
            .put("millimeter", "mmeter")
            .put("nanometer", "nanomete")
            .put("picometer", "pmeter")
            .put("microgram", "migram")
            .put("horsepower", "horsep")
            .put("milliwatt", "mwatt")
            .put("hectopascal", "hpascal")
            .put("temperature", "temp")
            .put("fahrenheit", "fahren")
            .put("centiliter", "cliter")
            .put("hectoliter", "hliter")
            .put("megaliter", "megliter")
            .put("milliliter", "mliter")
            .put("tablespoon", "tblspoon")
            .build();
        
        for (Entry<LstrType, Map<Status, Set<String>>> e1 : validity.getData().entrySet()) {
            LstrType lstrType = e1.getKey();
            for (Entry<Status, Set<String>> e2 : e1.getValue().entrySet()) {
                Status status = e2.getKey();
                for (String code : e2.getValue()) {
                    StringBuilder fixed = new StringBuilder();
                    for (String subcode : HYPHEN_SPLITTER.split(code)) {
                        if (fixed.length() > 0) {
                            fixed.append('-');
                        }
                        if (!allowed.containsAll(subcode)) {
                            errln("subcode has illegal character: " + subcode + ", in " + code);
                        } else if (subcode.length() > 8) {
                            fixed.append(shorten(subcode, shortened));
                        } else {
                            fixed.append(subcode);
                        }
                    }
                    String fixedCode = fixed.toString();
                    if (!fixedCode.equals(code)) {
                        warnln("code has overlong subcode: " + code + " should be " + fixedCode);
                    }
                }
            }
        }
        for (Entry<String, String> e : shortened.entrySet()) {
            System.out.println('"' + e.getKey() + "\", \"" + e.getValue() + "\",");
        }
    }

    
    private String shorten(String subcode, Map<String, String> shortened) {
        String result = shortened.get(subcode);
        if (result != null) return result;
        
        switch (subcode) {
        case "temperature": result = "temp"; break;
        case "acceleration": result =  "accel"; break;
        case "frequency": result =  "freq"; break;
        default: result = subcode.substring(0, 8); break;
        }
        shortened.put(subcode, result);
        return result;
    }

}
