package com.wust.dormitory.audit;

import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureAccessService featureAccessService;

    public AuditQueryService(
            NamedParameterJdbcTemplate jdbc,
            FeatureAccessService featureAccessService) {
        this.jdbc = jdbc;
        this.featureAccessService = featureAccessService;
    }

    public Map<String, Object> query(AuditQuery request) {
        featureAccessService.require(FeatureCodes.P2_AUDIT_ADVANCED_QUERY);
        AuditQuery normalized = request.normalized();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        append(where, parameters, " AND audit.occurred_at>=:occurredFrom", "occurredFrom", normalized.occurredFrom());
        append(where, parameters, " AND audit.occurred_at<:occurredTo", "occurredTo", normalized.occurredTo());
        append(where, parameters, " AND audit.operator_user_id=:operatorId", "operatorId", normalized.operatorId());
        append(where, parameters, " AND audit.operator_type=:operatorRole", "operatorRole", normalized.operatorRole());
        appendLike(where, parameters, " AND audit.action_type LIKE :module", "module", normalized.module(), "%s%%");
        append(where, parameters, " AND audit.action_type=:actionType", "actionType", normalized.actionType());
        append(where, parameters, " AND audit.resource_type=:targetType", "targetType", normalized.targetType());
        append(where, parameters, " AND audit.resource_id=:targetId", "targetId", normalized.targetId());
        if (normalized.success() != null) {
            where.append(" AND audit.result_status=:resultStatus");
            parameters.addValue("resultStatus", normalized.success() ? "SUCCESS" : "FAILED");
        }
        append(where, parameters, " AND audit.error_code=:errorCode", "errorCode", normalized.errorCode());
        append(where, parameters, " AND audit.request_id=:requestId", "requestId", normalized.requestId());
        append(where, parameters, " AND audit.network_address=:networkAddress", "networkAddress", normalized.networkAddress());
        if (!normalized.keyword().isBlank()) {
            where.append(" AND (audit.action_type LIKE :keyword OR audit.resource_type LIKE :keyword OR audit.resource_id LIKE :keyword OR audit.reason LIKE :keyword)");
            parameters.addValue("keyword", "%" + normalized.keyword() + "%");
        }
        parameters.addValue("size", normalized.size());
        parameters.addValue("offset", (normalized.page() - 1) * normalized.size());

        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log audit" + where,
                parameters,
                Integer.class);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT audit.id, audit.request_id, audit.operator_user_id,
                       audit.operator_type, audit.action_type, audit.resource_type,
                       audit.resource_id, audit.result_status, audit.error_code,
                       audit.network_address, audit.reason, audit.before_data,
                       audit.after_data, audit.occurred_at
                FROM audit_log audit
                """ + where + " ORDER BY audit.occurred_at DESC, audit.id DESC LIMIT :size OFFSET :offset",
                parameters);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", rows);
        result.put("page", normalized.page());
        result.put("size", normalized.size());
        result.put("total", total == null ? 0 : total);
        result.put("filters", normalized.asMap());
        return result;
    }

    private void append(
            StringBuilder where,
            MapSqlParameterSource parameters,
            String clause,
            String name,
            Object value) {
        if (value == null || value instanceof String text && text.isBlank()) {
            return;
        }
        where.append(clause);
        parameters.addValue(name, value);
    }

    private void appendLike(
            StringBuilder where,
            MapSqlParameterSource parameters,
            String clause,
            String name,
            String value,
            String pattern) {
        if (value == null || value.isBlank()) {
            return;
        }
        where.append(clause);
        parameters.addValue(name, pattern.formatted(value));
    }

    public record AuditQuery(
            LocalDateTime occurredFrom,
            LocalDateTime occurredTo,
            Long operatorId,
            String operatorRole,
            String module,
            String actionType,
            String targetType,
            String targetId,
            Boolean success,
            String errorCode,
            String requestId,
            String networkAddress,
            String keyword,
            Integer page,
            Integer size) {

        public AuditQuery normalized() {
            LocalDateTime to = occurredTo;
            LocalDateTime from = occurredFrom;
            if (from != null && to != null && !from.isBefore(to)) {
                throw new IllegalArgumentException("审计开始时间必须早于结束时间");
            }
            return new AuditQuery(
                    from,
                    to,
                    operatorId,
                    clean(operatorRole),
                    clean(module),
                    clean(actionType),
                    clean(targetType),
                    clean(targetId),
                    success,
                    clean(errorCode),
                    clean(requestId),
                    clean(networkAddress),
                    clean(keyword),
                    page == null ? 1 : Math.max(1, page),
                    size == null ? 50 : Math.max(1, Math.min(size, 200)));
        }

        public Map<String, Object> asMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("occurredFrom", occurredFrom);
            values.put("occurredTo", occurredTo);
            values.put("operatorId", operatorId);
            values.put("operatorRole", operatorRole);
            values.put("module", module);
            values.put("actionType", actionType);
            values.put("targetType", targetType);
            values.put("targetId", targetId);
            values.put("success", success);
            values.put("errorCode", errorCode);
            values.put("requestId", requestId);
            values.put("networkAddress", networkAddress);
            values.put("keyword", keyword);
            return values;
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
