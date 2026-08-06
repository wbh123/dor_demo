package com.wust.dormitory.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.json.JdbcJsonNormalizer;
import com.wust.dormitory.security.CurrentUser;
import org.slf4j.MDC;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public AuditService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public void success(CurrentUser user, String action, String resourceType, Object resourceId,
                        String reason, Object before, Object after) {
        write(user, action, resourceType, resourceId, "SUCCESS", reason, before, after);
    }

    public void write(CurrentUser user, String action, String resourceType, Object resourceId,
                      String result, String reason, Object before, Object after) {
        jdbc.update("""
                INSERT INTO audit_log
                (request_id, operator_user_id, operator_type, action_type, resource_type,
                 resource_id, result_status, reason, before_data, after_data, occurred_at)
                VALUES
                (:requestId, :operatorId, :operatorType, :action, :resourceType,
                 :resourceId, :result, :reason, CAST(:beforeData AS JSON), CAST(:afterData AS JSON), CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("requestId", MDC.get("requestId"))
                .addValue("operatorId", user == null ? null : user.userId())
                .addValue("operatorType", user == null ? "SYSTEM" : user.userType())
                .addValue("action", action)
                .addValue("resourceType", resourceType)
                .addValue("resourceId", resourceId == null ? null : String.valueOf(resourceId))
                .addValue("result", result)
                .addValue("reason", reason)
                .addValue("beforeData", json(before))
                .addValue("afterData", json(after)));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(JdbcJsonNormalizer.normalize(value));
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
