package com.wust.dormitory.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public NotificationService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void sendInApp(
            long studentId,
            String notificationType,
            String titleKey,
            String messageKey,
            Map<String, ?> parameters) {
        jdbc.update("""
                INSERT INTO student_notification
                (student_id, notification_type, title_key, message_key, parameters_json)
                VALUES
                (:studentId,:notificationType,:titleKey,:messageKey,CAST(:parametersJson AS JSON))
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("notificationType", notificationType)
                .addValue("titleKey", titleKey)
                .addValue("messageKey", messageKey)
                .addValue("parametersJson", json(parameters)));
    }

    public void sendInAppBulk(
            List<Long> studentIds,
            String notificationType,
            String titleKey,
            String messageKey,
            Map<String, ?> parameters) {
        for (Long studentId : studentIds) {
            sendInApp(studentId, notificationType, titleKey, messageKey, parameters);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("通知参数无法序列化", exception);
        }
    }
}
