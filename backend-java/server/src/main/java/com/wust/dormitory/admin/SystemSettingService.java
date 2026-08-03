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
    private static final int MAX_STORED_VALUE_LENGTH = 12000;
    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            "zh-CN", "欢迎使用示例大学学生宿舍智能选择系统。请先完成个人偏好，再选择合适的宿舍或床位。",
            "en-US", "Welcome to the university dormitory selection system. Complete your personal preferences first, then choose a suitable room or bed.");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public SystemSettingService(NamedParameterJdbcTemplate jdbc, AuditService auditService, ObjectMapper objectMapper) {
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
            Map<String, String> messages,
            Map<String, String> countryMessages,
            int expectedVersion,
            CurrentUser operator) {
        ensureStudentWelcomeSetting();
        WelcomeConfiguration configuration = new WelcomeConfiguration(
                normalizeMessages(messages), normalizeCountryMessages(countryMessages));
        String serialized = json(Map.of(
                "messages", configuration.messages(),
                "countryMessages", configuration.countryMessages()));
        if (serialized.length() > MAX_STORED_VALUE_LENGTH) {
            throw new BusinessException("STUDENT_WELCOME_MESSAGE_INVALID", "欢迎语配置内容过长，请减少国家数量或精简文本");
        }

        Map<String, Object> before = one();
        int actualVersion = ((Number) before.get("version")).intValue();
        if (actualVersion != expectedVersion) {
            throw new BusinessException("SYSTEM_SETTING_VERSION_CONFLICT", "欢迎语已经被其他管理员修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        int updated = jdbc.update("""
                UPDATE system_setting
                SET setting_value=:messages, updated_by=:updatedBy, version=version+1
                WHERE setting_key=:settingKey AND version=:expectedVersion
                """, new MapSqlParameterSource().addValue("messages", serialized)
                .addValue("updatedBy", operator.userId()).addValue("settingKey", STUDENT_WELCOME_MESSAGE)
                .addValue("expectedVersion", expectedVersion));
        if (updated != 1) {
            throw new BusinessException("SYSTEM_SETTING_VERSION_CONFLICT", "欢迎语已经被其他管理员修改，请刷新后重试", HttpStatus.CONFLICT);
        }
        Map<String, Object> after = one();
        auditService.success(operator, "SYSTEM_SETTING_UPDATE", "SYSTEM_SETTING", before.get("id"),
                "更新按国家匹配的新生欢迎语", before, after);
        return after;
    }

    public WelcomeConfiguration readConfiguration(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) return new WelcomeConfiguration(new LinkedHashMap<>(DEFAULT_MESSAGES), Map.of());
        try {
            Map<String, Object> parsed = objectMapper.readValue(rawValue, new TypeReference<Map<String, Object>>() { });
            if (parsed == null) return new WelcomeConfiguration(new LinkedHashMap<>(DEFAULT_MESSAGES), Map.of());
            if (parsed.containsKey("messages") || parsed.containsKey("countryMessages")) {
                return new WelcomeConfiguration(
                        mergeMessages(stringMap(parsed.get("messages"))),
                        normalizeStoredCountryMessages(stringMap(parsed.get("countryMessages"))));
            }
            // 兼容旧版扁平的 zh-CN/en-US 对象。
            return new WelcomeConfiguration(mergeMessages(stringMap(parsed)), Map.of());
        } catch (JsonProcessingException ignored) {
            Map<String, String> fallback = new LinkedHashMap<>(DEFAULT_MESSAGES);
            fallback.put("zh-CN", rawValue.trim());
            return new WelcomeConfiguration(fallback, Map.of());
        }
    }

    public Map<String, String> readMessages(String rawValue) {
        return readConfiguration(rawValue).messages();
    }

    private void ensureStudentWelcomeSetting() {
        jdbc.update("""
                INSERT INTO system_setting (setting_key, setting_value, version, updated_by)
                VALUES (:settingKey, :settingValue, 0, NULL)
                ON DUPLICATE KEY UPDATE setting_key=VALUES(setting_key)
                """, new MapSqlParameterSource().addValue("settingKey", STUDENT_WELCOME_MESSAGE)
                .addValue("settingValue", json(Map.of("messages", DEFAULT_MESSAGES, "countryMessages", Map.of()))));
    }

    private Map<String, String> normalizeMessages(Map<String, String> messages) {
        if (messages == null) throw invalidMessage();
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String locale : List.of("zh-CN", "en-US")) {
            String value = messages.get(locale);
            if (value == null || value.trim().isEmpty() || value.trim().length() > 1000) throw invalidMessage();
            normalized.put(locale, value.trim());
        }
        return normalized;
    }

    private Map<String, String> normalizeCountryMessages(Map<String, String> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, String> normalized = new LinkedHashMap<>();
        values.forEach((code, message) -> {
            String normalizedCode = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
            String normalizedMessage = message == null ? "" : message.trim();
            if (!normalizedCode.matches("^[A-Z]{2}$")) throw new BusinessException("WELCOME_COUNTRY_INVALID", "国家或地区代码必须为两位大写字母");
            if (normalizedMessage.isEmpty() || normalizedMessage.length() > 1000) throw new BusinessException("WELCOME_COUNTRY_MESSAGE_INVALID", "国家欢迎语长度必须为1至1000个字符");
            normalized.put(normalizedCode, normalizedMessage);
        });
        return normalized;
    }

    private Map<String, String> mergeMessages(Map<String, String> values) {
        Map<String, String> merged = new LinkedHashMap<>(DEFAULT_MESSAGES);
        values.forEach((key, value) -> { if (value != null && !value.isBlank()) merged.put(key, value.trim()); });
        return merged;
    }

    private Map<String, String> normalizeStoredCountryMessages(Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((code, value) -> {
            if (code != null && code.matches("^[A-Za-z]{2}$") && value != null && !value.isBlank())
                result.put(code.toUpperCase(Locale.ROOT), value.trim());
        });
        return result;
    }

    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, item) -> { if (key != null && item != null) result.put(String.valueOf(key), String.valueOf(item)); });
        return result;
    }

    private BusinessException invalidMessage() {
        return new BusinessException("STUDENT_WELCOME_MESSAGE_INVALID", "中文和英文欢迎语均不能为空，且单项长度必须为1至1000个字符");
    }

    private Map<String, Object> one() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT setting.id, setting.setting_value, setting.version, setting.updated_at,
                       updater.display_name AS updated_by_name
                FROM system_setting setting LEFT JOIN app_user updater ON updater.id=setting.updated_by
                WHERE setting.setting_key=:settingKey
                """, Map.of("settingKey", STUDENT_WELCOME_MESSAGE));
        if (rows.isEmpty()) throw new BusinessException("SYSTEM_SETTING_NOT_FOUND", "学生欢迎语配置不存在", HttpStatus.NOT_FOUND);
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        WelcomeConfiguration configuration = readConfiguration(String.valueOf(result.remove("setting_value")));
        result.put("messages", configuration.messages());
        result.put("countryMessages", configuration.countryMessages());
        result.put("message", configuration.messages().get("zh-CN"));
        return result;
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException("JSON_ERROR", "欢迎语序列化失败", HttpStatus.INTERNAL_SERVER_ERROR); }
    }

    public record WelcomeConfiguration(Map<String, String> messages, Map<String, String> countryMessages) { }
}
