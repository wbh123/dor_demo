package com.wust.dormitory.audit.model.persistence;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public record RecentAuditLogRow(
        Long id,
        String requestId,
        Long operatorUserId,
        String operatorType,
        String actionType,
        String resourceType,
        String resourceId,
        String resultStatus,
        String reason,
        LocalDateTime occurredAt) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", id);
        response.put("request_id", requestId);
        response.put("operator_user_id", operatorUserId);
        response.put("operator_type", operatorType);
        response.put("action_type", actionType);
        response.put("resource_type", resourceType);
        response.put("resource_id", resourceId);
        response.put("result_status", resultStatus);
        response.put("reason", reason);
        response.put("occurred_at", occurredAt);
        return response;
    }
}
