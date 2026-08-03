package com.wust.dormitory.subscription;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class PlatformAuditService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public PlatformAuditService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void success(String operationType, Long operatorId, String targetType, String targetId,
                        String reason, Object before, Object after) {
        write(operationType, operatorId, targetType, targetId, reason, before, after, true, null);
    }

    public void failure(String operationType, Long operatorId, String targetType, String targetId,
                        String reason, Object before, Object after, String errorCode) {
        write(operationType, operatorId, targetType, targetId, reason, before, after, false, errorCode);
    }

    private void write(String operationType, Long operatorId, String targetType, String targetId,
                       String reason, Object before, Object after, boolean success, String errorCode) {
        jdbc.update("""
                INSERT INTO platform_audit_log
                (operation_type, operator_user_id, target_type, target_id, change_reason,
                 before_json, after_json, request_id, success, error_code)
                VALUES
                (:operationType, :operatorId, :targetType, :targetId, :reason,
                 CAST(:beforeJson AS JSON), CAST(:afterJson AS JSON), NULL, :success, :errorCode)
                """, new MapSqlParameterSource()
                .addValue("operationType", operationType)
                .addValue("operatorId", operatorId)
                .addValue("targetType", targetType)
                .addValue("targetId", targetId)
                .addValue("reason", reason)
                .addValue("beforeJson", json(before))
                .addValue("afterJson", json(after))
                .addValue("success", success ? 1 : 0)
                .addValue("errorCode", errorCode));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return objectMapper.valueToTree(Map.of("serializationError", exception.getMessage())).toString();
        }
    }
}
