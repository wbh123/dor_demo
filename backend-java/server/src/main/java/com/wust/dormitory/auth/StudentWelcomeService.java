package com.wust.dormitory.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.model.dto.WelcomeData;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentWelcomeService {
    static final String STUDENT_WELCOME_MESSAGE = "STUDENT_WELCOME_MESSAGE";
    private static final Map<String, String> DEFAULT_MESSAGES = Map.of(
            "zh-CN", "欢迎加入示例大学宿舍智能选择系统。请先完善个人偏好，再选择合适的宿舍与床位。",
            "en-US", "Welcome to the Wuhan University of Science and Technology dormitory selection system. Complete your personal preferences first, then choose a suitable room and bed.");

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public StudentWelcomeService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public WelcomeData welcomeFor(CurrentUser user) {
        if (user == null || !"STUDENT".equals(user.userType())) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT welcome_acknowledged_at
                FROM app_user
                WHERE id=:userId
                """, Map.of("userId", user.userId()));
        if (rows.isEmpty()) {
            return null;
        }
        String rawValue = jdbc.query("""
                SELECT setting_value
                FROM system_setting
                WHERE setting_key=:settingKey
                """, Map.of("settingKey", STUDENT_WELCOME_MESSAGE),
                resultSet -> resultSet.next() ? resultSet.getString(1) : null);
        Map<String, String> messages = readMessages(rawValue);

        WelcomeData data = new WelcomeData();
        data.setRequired(rows.getFirst().get("welcome_acknowledged_at") == null);
        data.setTitle("新同学，欢迎你");
        data.setMessages(messages);
        data.setMessage(messages.get("zh-CN"));
        return data;
    }

    private Map<String, String> readMessages(String rawValue) {
        Map<String, String> result = new LinkedHashMap<>(DEFAULT_MESSAGES);
        if (rawValue == null || rawValue.isBlank()) {
            return result;
        }
        try {
            Map<String, String> configured = objectMapper.readValue(
                    rawValue,
                    new TypeReference<Map<String, String>>() { });
            if (configured == null) {
                return result;
            }
            configured.forEach((locale, message) -> {
                if (message != null && !message.isBlank()) {
                    result.put(locale, message.trim());
                }
            });
        } catch (JsonProcessingException ignored) {
            result.put("zh-CN", rawValue.trim());
        }
        return result;
    }

    public void acknowledge(CurrentUser user) {
        if (user == null || !"STUDENT".equals(user.userType())) {
            throw new BusinessException(
                    "STUDENT_WELCOME_FORBIDDEN",
                    "只有学生账号可以确认欢迎信息",
                    HttpStatus.FORBIDDEN);
        }
        jdbc.update("""
                UPDATE app_user
                SET welcome_acknowledged_at=COALESCE(welcome_acknowledged_at, CURRENT_TIMESTAMP(3))
                WHERE id=:userId AND user_type='STUDENT'
                """, Map.of("userId", user.userId()));
    }
}
