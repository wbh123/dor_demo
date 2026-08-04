package com.wust.dormitory.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SystemSettingService {
    private static final String STUDENT_WELCOME_MESSAGE = "STUDENT_WELCOME_MESSAGE";
    static final String FALLBACK_WELCOME_LOCALE = "en-US";
    private static final String PRIMARY_WELCOME_LOCALE = "zh-CN";
    private static final int MAX_STORED_VALUE_LENGTH = 12000;
    private static final int MAX_LOCALE_COUNT = 20;
    private static final int MAX_COUNTRY_MESSAGE_COUNT = 80;
    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            PRIMARY_WELCOME_LOCALE,
            "欢迎使用示例大学学生宿舍智能选择系统。请先完成个人偏好，再选择合适的宿舍或床位。",
            FALLBACK_WELCOME_LOCALE,
            "Welcome to the university dormitory selection system. Complete your personal preferences first, then choose a suitable room or bed.");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SystemSettingService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> studentWelcome() {
        ensureStudentWelcomeSetting();
        return one();
    }

    @Transactional
    public Map<String, Object> updateStudentWelcome(
            Map<String, String> languageMessages,
            Map<String, String> countryMessages,
            int expectedVersion,
            CurrentUser operator) {
        ensureStudentWelcomeSetting();
        Map<String, String> normalizedMessages = normalizeLocaleMessages(languageMessages);
        Map<String, String> normalizedCountryMessages = normalizeCountryMessages(countryMessages);
        String serialized = json(Map.of(
                "messages", normalizedMessages,
                "countryMessages", normalizedCountryMessages));
        if (serialized.length() > MAX_STORED_VALUE_LENGTH) {
            throw new BusinessException(
                    "STUDENT_WELCOME_MESSAGE_INVALID",
                    "欢迎语配置内容过长，请减少语言或国家地区数量，或精简文本");
        }

        Map<String, Object> before = one();
        int actualVersion = ((Number) before.get("version")).intValue();
        if (actualVersion != expectedVersion) {
            throw new BusinessException(
                    "SYSTEM_SETTING_VERSION_CONFLICT",
                    "欢迎语已经被其他管理员修改，请刷新后重试",
                    HttpStatus.CONFLICT);
        }
        int updated = jdbc.update("""
                UPDATE system_setting
                SET setting_value=:messages, updated_by=:updatedBy, version=version+1
                WHERE setting_key=:settingKey AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("messages", serialized)
                .addValue("updatedBy", operator.userId())
                .addValue("settingKey", STUDENT_WELCOME_MESSAGE)
                .addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new BusinessException(
                    "SYSTEM_SETTING_VERSION_CONFLICT",
                    "欢迎语已经被其他管理员修改，请刷新后重试",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> after = one();
        auditService.success(
                operator,
                "SYSTEM_SETTING_UPDATE",
                "SYSTEM_SETTING",
                before.get("id"),
                "更新新生欢迎语语言与国家地区版本",
                before,
                after);
        return after;
    }

    public WelcomeConfiguration readConfiguration(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultConfiguration();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    rawValue,
                    new TypeReference<Map<String, Object>>() { });
            if (parsed == null) return defaultConfiguration();
            if (parsed.containsKey("messages")) {
                return new WelcomeConfiguration(
                        mergeMessages(stringMap(parsed.get("messages"))),
                        mergeCountryMessages(stringMap(parsed.get("countryMessages"))));
            }
            // 兼容旧版扁平 zh-CN/en-US 对象。
            return new WelcomeConfiguration(mergeMessages(stringMap(parsed)), Map.of());
        } catch (JsonProcessingException ignored) {
            Map<String, String> fallback = new LinkedHashMap<>(DEFAULT_MESSAGES);
            fallback.put(PRIMARY_WELCOME_LOCALE, rawValue.trim());
            return new WelcomeConfiguration(fallback, Map.of());
        }
    }

    public Map<String, String> readMessages(String rawValue) {
        return readConfiguration(rawValue).messages();
    }

    private WelcomeConfiguration defaultConfiguration() {
        return new WelcomeConfiguration(new LinkedHashMap<>(DEFAULT_MESSAGES), Map.of());
    }

    private void ensureStudentWelcomeSetting() {
        jdbc.update("""
                INSERT INTO system_setting (setting_key, setting_value, version, updated_by)
                VALUES (:settingKey, :settingValue, 0, NULL)
                ON DUPLICATE KEY UPDATE setting_key=VALUES(setting_key)
                """, new MapSqlParameterSource()
                .addValue("settingKey", STUDENT_WELCOME_MESSAGE)
                .addValue("settingValue", json(Map.of(
                        "messages", DEFAULT_MESSAGES,
                        "countryMessages", Map.of()))));
    }

    Map<String, String> normalizeLocaleMessages(Map<String, String> values) {
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
        if (!normalized.containsKey(PRIMARY_WELCOME_LOCALE)
                || !normalized.containsKey(FALLBACK_WELCOME_LOCALE)) {
            throw invalidMessage();
        }
        return normalized;
    }

    Map<String, String> normalizeCountryMessages(Map<String, String> values) {
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

    String normalizeLocaleTag(String value) {
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

    String normalizeCountryCode(String value) {
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

    private String normalizeMessage(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 1000) throw invalidMessage();
        return normalized;
    }

    private Map<String, String> mergeMessages(Map<String, String> values) {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_MESSAGES);
        values.forEach((key, value) -> {
            if (key == null || value == null || value.isBlank()) return;
            try {
                merged.put(normalizeLocaleTag(key), value.trim());
            } catch (BusinessException ignored) {
                // 读取历史数据时忽略不合法语言代码，管理员下次保存时会得到明确校验提示。
            }
        });
        if (!merged.containsKey(FALLBACK_WELCOME_LOCALE)) {
            merged.put(FALLBACK_WELCOME_LOCALE, DEFAULT_MESSAGES.get(FALLBACK_WELCOME_LOCALE));
        }
        return merged;
    }

    private Map<String, String> mergeCountryMessages(Map<String, String> values) {
        Map<String, String> merged = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || value == null || value.isBlank()) return;
            try {
                merged.put(normalizeCountryCode(key), value.trim());
            } catch (BusinessException ignored) {
                // 读取历史数据时忽略不合法国家地区代码，保存时再给出明确提示。
            }
        });
        return merged;
    }

    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (key != null && item != null) result.put(String.valueOf(key), String.valueOf(item));
        });
        return result;
    }

    private BusinessException invalidMessage() {
        return new BusinessException(
                "STUDENT_WELCOME_MESSAGE_INVALID",
                "欢迎语长度必须为1至1000个字符；中文和英文基础版本必须配置");
    }

    private Map<String, Object> one() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT setting.id, setting.setting_value, setting.version, setting.updated_at,
                       updater.display_name AS updated_by_name
                FROM system_setting setting
                LEFT JOIN app_user updater ON updater.id=setting.updated_by
                WHERE setting.setting_key=:settingKey
                """, Map.of("settingKey", STUDENT_WELCOME_MESSAGE));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "SYSTEM_SETTING_NOT_FOUND",
                    "学生欢迎语配置不存在",
                    HttpStatus.NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        WelcomeConfiguration configuration = readConfiguration(
                String.valueOf(result.remove("setting_value")));
        result.put("messages", configuration.messages());
        result.put("countryMessages", configuration.countryMessages());
        result.put("fallbackLocale", FALLBACK_WELCOME_LOCALE);
        result.put("message", configuration.messages().get(PRIMARY_WELCOME_LOCALE));
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    "JSON_ERROR",
                    "欢迎语序列化失败",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public record WelcomeConfiguration(
            Map<String, String> messages,
            Map<String, String> countryMessages) { }
}
