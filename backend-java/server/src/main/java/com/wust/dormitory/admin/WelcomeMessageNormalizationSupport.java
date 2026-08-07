package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class WelcomeMessageNormalizationSupport {
    private static final int MAX_LOCALE_COUNT = 20;
    private static final int MAX_COUNTRY_MESSAGE_COUNT = 80;

    private WelcomeMessageNormalizationSupport() {
    }

    static Map<String, String> normalizeLocaleMessages(
            Map<String, String> values,
            String primaryLocale,
            String fallbackLocale) {
        if (values == null || values.size() < 2 || values.size() > MAX_LOCALE_COUNT) {
            throw invalidMessage();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((locale, message) -> {
            String normalizedLocale = normalizeLocaleTag(locale);
            String normalizedMessage = normalizeMessage(message);
            if (normalized.putIfAbsent(normalizedLocale, normalizedMessage) != null) {
                throw new BusinessException(
                        "WELCOME_LOCALE_DUPLICATED",
                        "欢迎语语言代码重复：" + normalizedLocale);
            }
        });
        if (!normalized.containsKey(primaryLocale) || !normalized.containsKey(fallbackLocale)) {
            throw invalidMessage();
        }
        return normalized;
    }

    static Map<String, String> normalizeCountryMessages(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        if (values.size() > MAX_COUNTRY_MESSAGE_COUNT) {
            throw new BusinessException(
                    "WELCOME_COUNTRY_LIMIT_EXCEEDED",
                    "国家或地区专属欢迎语最多配置80项");
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((country, message) -> {
            String normalizedCountry = normalizeCountryCode(country);
            String normalizedMessage = normalizeMessage(message);
            if (normalized.putIfAbsent(normalizedCountry, normalizedMessage) != null) {
                throw new BusinessException(
                        "WELCOME_COUNTRY_DUPLICATED",
                        "国家或地区欢迎语重复：" + normalizedCountry);
            }
        });
        return normalized;
    }

    static String normalizeLocaleTag(String value) {
        String source = value == null ? "" : value.trim().replace('_', '-');
        if (!source.matches("^[A-Za-z]{2,3}(?:-[A-Za-z]{2}|-[A-Za-z]{4})?(?:-[A-Za-z]{2}|-[0-9]{3})?$")) {
            throw new BusinessException(
                    "WELCOME_LOCALE_INVALID",
                    "语言代码必须使用类似 zh-CN、en-US 或 fr-FR 的格式");
        }
        String[] parts = source.split("-");
        StringBuilder normalized = new StringBuilder(parts[0].toLowerCase(Locale.ROOT));
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index];
            normalized.append('-');
            if (part.length() == 4) {
                normalized.append(part.substring(0, 1).toUpperCase(Locale.ROOT))
                        .append(part.substring(1).toLowerCase(Locale.ROOT));
            } else {
                normalized.append(part.toUpperCase(Locale.ROOT));
            }
        }
        return normalized.toString();
    }

    static String normalizeCountryCode(String value) {
        String source = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!source.matches("^[A-Z]{2}$")) {
            throw new BusinessException(
                    "WELCOME_COUNTRY_INVALID",
                    "国家或地区必须使用ISO二位代码，例如CN、JP或US");
        }
        try {
            return CountryRegionCatalog.code(source, "INTERNATIONAL");
        } catch (BusinessException exception) {
            throw new BusinessException(
                    "WELCOME_COUNTRY_INVALID",
                    "无法识别国家或地区代码：" + source);
        }
    }

    private static String normalizeMessage(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 1000) throw invalidMessage();
        return normalized;
    }

    private static BusinessException invalidMessage() {
        return new BusinessException(
                "STUDENT_WELCOME_MESSAGE_INVALID",
                "欢迎语长度必须为1至1000个字符；中文和英文基础版本必须配置");
    }
}
