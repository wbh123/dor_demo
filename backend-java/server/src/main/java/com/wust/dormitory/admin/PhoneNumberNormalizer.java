package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;

import java.util.Map;

final class PhoneNumberNormalizer {
    private static final Map<String, String> DIAL_CODES = Map.ofEntries(
            Map.entry("AF", "93"), Map.entry("AL", "355"), Map.entry("DZ", "213"), Map.entry("AO", "244"),
            Map.entry("AR", "54"), Map.entry("AU", "61"), Map.entry("AT", "43"), Map.entry("BH", "973"),
            Map.entry("BD", "880"), Map.entry("BY", "375"), Map.entry("BE", "32"), Map.entry("BZ", "501"),
            Map.entry("BJ", "229"), Map.entry("BT", "975"), Map.entry("BO", "591"), Map.entry("BA", "387"),
            Map.entry("BW", "267"), Map.entry("BR", "55"), Map.entry("BN", "673"), Map.entry("BG", "359"),
            Map.entry("BF", "226"), Map.entry("BI", "257"), Map.entry("KH", "855"), Map.entry("CM", "237"),
            Map.entry("CA", "1"), Map.entry("CV", "238"), Map.entry("TD", "235"), Map.entry("CL", "56"),
            Map.entry("CN", "86"), Map.entry("CO", "57"), Map.entry("KM", "269"), Map.entry("CG", "242"),
            Map.entry("CD", "243"), Map.entry("CR", "506"), Map.entry("CI", "225"), Map.entry("HR", "385"),
            Map.entry("CU", "53"), Map.entry("CY", "357"), Map.entry("CZ", "420"), Map.entry("DK", "45"),
            Map.entry("DJ", "253"), Map.entry("DO", "1"), Map.entry("EC", "593"), Map.entry("EG", "20"),
            Map.entry("SV", "503"), Map.entry("GQ", "240"), Map.entry("ER", "291"), Map.entry("EE", "372"),
            Map.entry("SZ", "268"), Map.entry("ET", "251"), Map.entry("FJ", "679"), Map.entry("FI", "358"),
            Map.entry("FR", "33"), Map.entry("GA", "241"), Map.entry("GM", "220"), Map.entry("DE", "49"),
            Map.entry("GH", "233"), Map.entry("GR", "30"), Map.entry("GT", "502"), Map.entry("GN", "224"),
            Map.entry("GW", "245"), Map.entry("GY", "592"), Map.entry("HT", "509"), Map.entry("HN", "504"),
            Map.entry("HK", "852"), Map.entry("HU", "36"), Map.entry("IS", "354"), Map.entry("IN", "91"),
            Map.entry("ID", "62"), Map.entry("IR", "98"), Map.entry("IQ", "964"), Map.entry("IE", "353"),
            Map.entry("IL", "972"), Map.entry("IT", "39"), Map.entry("JM", "1"), Map.entry("JP", "81"),
            Map.entry("JO", "962"), Map.entry("KZ", "7"), Map.entry("KE", "254"), Map.entry("KI", "686"),
            Map.entry("KP", "850"), Map.entry("KR", "82"), Map.entry("KW", "965"), Map.entry("KG", "996"),
            Map.entry("LA", "856"), Map.entry("LV", "371"), Map.entry("LB", "961"), Map.entry("LS", "266"),
            Map.entry("LR", "231"), Map.entry("LY", "218"), Map.entry("LT", "370"), Map.entry("LU", "352"),
            Map.entry("MG", "261"), Map.entry("MW", "265"), Map.entry("MY", "60"), Map.entry("MV", "960"),
            Map.entry("ML", "223"), Map.entry("MT", "356"), Map.entry("MH", "692"), Map.entry("MR", "222"),
            Map.entry("MU", "230"), Map.entry("MX", "52"), Map.entry("FM", "691"), Map.entry("MD", "373"),
            Map.entry("MN", "976"), Map.entry("ME", "382"), Map.entry("MA", "212"), Map.entry("MZ", "258"),
            Map.entry("MM", "95"), Map.entry("NA", "264"), Map.entry("NR", "674"), Map.entry("NP", "977"),
            Map.entry("NL", "31"), Map.entry("NZ", "64"), Map.entry("NI", "505"), Map.entry("NE", "227"),
            Map.entry("NG", "234"), Map.entry("MK", "389"), Map.entry("NO", "47"), Map.entry("OM", "968"),
            Map.entry("PK", "92"), Map.entry("PW", "680"), Map.entry("PS", "970"), Map.entry("PA", "507"),
            Map.entry("PG", "675"), Map.entry("PY", "595"), Map.entry("PE", "51"), Map.entry("PH", "63"),
            Map.entry("PL", "48"), Map.entry("PT", "351"), Map.entry("QA", "974"), Map.entry("RO", "40"),
            Map.entry("RU", "7"), Map.entry("RW", "250"), Map.entry("WS", "685"), Map.entry("SA", "966"),
            Map.entry("SN", "221"), Map.entry("RS", "381"), Map.entry("SC", "248"), Map.entry("SL", "232"),
            Map.entry("SG", "65"), Map.entry("SK", "421"), Map.entry("SI", "386"), Map.entry("SB", "677"),
            Map.entry("SO", "252"), Map.entry("ZA", "27"), Map.entry("SS", "211"), Map.entry("ES", "34"),
            Map.entry("LK", "94"), Map.entry("SD", "249"), Map.entry("SR", "597"), Map.entry("SE", "46"),
            Map.entry("CH", "41"), Map.entry("SY", "963"), Map.entry("TW", "886"), Map.entry("TJ", "992"),
            Map.entry("TZ", "255"), Map.entry("TH", "66"), Map.entry("TL", "670"), Map.entry("TG", "228"),
            Map.entry("TO", "676"), Map.entry("TT", "1"), Map.entry("TN", "216"), Map.entry("TR", "90"),
            Map.entry("TM", "993"), Map.entry("TV", "688"), Map.entry("UG", "256"), Map.entry("UA", "380"),
            Map.entry("AE", "971"), Map.entry("GB", "44"), Map.entry("US", "1"), Map.entry("UY", "598"),
            Map.entry("UZ", "998"), Map.entry("VU", "678"), Map.entry("VE", "58"), Map.entry("VN", "84"),
            Map.entry("YE", "967"), Map.entry("ZM", "260"), Map.entry("ZW", "263"), Map.entry("MO", "853"),
            Map.entry("BS", "1")
    );

    private PhoneNumberNormalizer() {
    }

    static String normalize(String value, String nationalityCode) {
        if (value == null || value.isBlank()) return null;
        String source = value.trim();
        String digits = source.replaceAll("\\D", "");
        if (digits.isBlank()) return null;
        String normalized;
        if (source.startsWith("+")) {
            normalized = "+" + digits;
        } else {
            String dialCode = DIAL_CODES.get(nationalityCode == null ? "" : nationalityCode.toUpperCase());
            if (dialCode == null) {
                throw new BusinessException("PHONE_COUNTRY_CODE_REQUIRED", "无法根据国家或地区确定手机号国家码");
            }
            normalized = "+" + dialCode + digits.replaceFirst("^0+", "");
        }
        if (!normalized.matches("^\\+[1-9]\\d{5,14}$")) {
            throw new BusinessException("PHONE_NUMBER_INVALID", "手机号码必须包含国家码，例如+8613800000000");
        }
        return normalized;
    }

    static String dialCode(String countryCode) {
        return "+" + DIAL_CODES.getOrDefault(countryCode, "86");
    }
}
