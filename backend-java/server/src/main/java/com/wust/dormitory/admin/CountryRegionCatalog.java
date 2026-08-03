package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CountryRegionCatalog {
    private static final Locale CHINESE = Locale.SIMPLIFIED_CHINESE;
    private static final Map<String, String> NAME_TO_CODE = buildNameIndex();

    private CountryRegionCatalog() {
    }

    public static String code(String value, String studentCategory) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            if ("DOMESTIC".equals(studentCategory)) return "CN";
            throw new BusinessException("NATIONALITY_REQUIRED", "国际生必须选择国家或地区");
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (upper.matches("^[A-Z]{2}$") && Arrays.asList(Locale.getISOCountries()).contains(upper)) {
            return upper;
        }
        String code = NAME_TO_CODE.get(normalized.toLowerCase(Locale.ROOT));
        if (code == null) {
            throw new BusinessException("NATIONALITY_INVALID", "无法识别国家或地区：" + normalized);
        }
        return code;
    }

    public static String name(String code) {
        if (code == null || code.isBlank()) return "未填写";
        Locale locale = new Locale.Builder().setRegion(code.trim().toUpperCase(Locale.ROOT)).build();
        String name = locale.getDisplayCountry(CHINESE);
        return name == null || name.isBlank() ? code : name;
    }

    private static Map<String, String> buildNameIndex() {
        Map<String, String> result = new LinkedHashMap<>();
        for (String code : Locale.getISOCountries()) {
            Locale locale = new Locale.Builder().setRegion(code).build();
            add(result, code, code);
            add(result, locale.getDisplayCountry(CHINESE), code);
            add(result, locale.getDisplayCountry(Locale.ENGLISH), code);
        }
        add(result, "中国大陆", "CN");
        add(result, "中国香港", "HK");
        add(result, "香港", "HK");
        add(result, "中国澳门", "MO");
        add(result, "澳门", "MO");
        add(result, "中国台湾", "TW");
        add(result, "台湾", "TW");
        return Map.copyOf(result);
    }

    private static void add(Map<String, String> result, String name, String code) {
        if (name != null && !name.isBlank()) result.put(name.trim().toLowerCase(Locale.ROOT), code);
    }
}
