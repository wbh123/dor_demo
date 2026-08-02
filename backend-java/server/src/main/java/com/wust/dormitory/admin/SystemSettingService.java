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
import java.util.Map;

@Service
public class SystemSettingService {
    private static final String STUDENT_WELCOME_MESSAGE = "STUDENT_WELCOME_MESSAGE";
    private static final int MAX_STORED_VALUE_LENGTH = 1000;
    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            "zh-CN", "欢迎使用武汉科技大学学生宿舍智能选择系统。请先完成个人偏好，再选择合适的宿舍与床位。",
            "en-US", "Welcome to the Wuhan University of Science and Technology dormitory selection system. Complete your personal preferences first, then choose a suitable room and bed.");

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

    public Map<String, Object> studentWelcome() {
        return one();
    }

    @Transactional
    public Map<String, Object> updateStudentWelcome(
            Map<String, String> messages,
            int expectedVersion,
            CurrentUser operator) {
        Map<String, String> normalized = normalize(messages);
        String serializedMessages = json(normalized);
        if (serializedMessages.length() > MAX_STORED_VALUE_LENGTH) {
            throw new BusinessException(
                    "STUDENT_WELCOME_MESSAGE_INVALID",
                    "中英文欢迎语合计内容过长，请精简后重试");
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
                SET setting_value=:messages,
                    updated_by=:updatedBy,
                    version=version+1
                WHERE setting_key=:settingKey
                  AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("messages", serializedMessages)
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
                "更新多语言新生欢迎语",
                before,
                after);
        return after;
    }

    public Map<String, String> readMessages(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return new LinkedHashMap<>(DEFAULT_MESSAGES);
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(
                    rawValue,
                    new TypeReference<Map<String, String>>() { });
            Map<String, String> merged = new LinkedHashMap<>(DEFAULT_MESSAGES);
            for (String locale : List.of("zh-CN", "en-US")) {
                String value = parsed.get(locale);
                if (value != null && !value.isBlank()) {
                    merged.put(locale, value.trim());
                }
            }
            return merged;
        } catch (JsonProcessingException ignored) {
            Map<String, String> fallback = new LinkedHashMap<>(DEFAULT_MESSAGES);
            fallback.put("zh-CN", rawValue.trim());
            return fallback;
        }
    }

    private Map<String, String> normalize(Map<String, String> messages) {
        if (messages == null) {
            throw invalidMessage();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (String locale : List.of("zh-CN", "en-US")) {
            String value = messages.get(locale);
            if (value == null || value.trim().isEmpty() || value.trim().length() > 1000) {
                throw invalidMessage();
            }
            normalized.put(locale, value.trim());
        }
        return normalized;
    }

    private BusinessException invalidMessage() {
        return new BusinessException(
                "STUDENT_WELCOME_MESSAGE_INVALID",
                "中文和英文欢迎语均不能为空，且单项长度必须为1至1000个字符");
    }

    private Map<String, Object> one() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT setting.id,
                       setting.setting_value,
                       setting.version,
                       setting.updated_at,
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
        String rawValue = String.valueOf(result.remove("setting_value"));
        Map<String, String> messages = readMessages(rawValue);
        result.put("messages", messages);
        result.put("message", messages.get("zh-CN"));
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
}
