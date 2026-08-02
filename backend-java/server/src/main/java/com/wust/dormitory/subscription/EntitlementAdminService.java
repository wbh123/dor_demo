package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class EntitlementAdminService {
    private final NamedParameterJdbcTemplate jdbc;
    private final SubscriptionService subscriptionService;
    private final PlatformAuditService auditService;

    public EntitlementAdminService(NamedParameterJdbcTemplate jdbc,
                                   SubscriptionService subscriptionService,
                                   PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.subscriptionService = subscriptionService;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> features() {
        return jdbc.queryForList("""
                SELECT feature_code, feature_name, phase, scope, granularity,
                       action_type, risk_level, enabled_in_program, sort_order
                FROM feature_catalog ORDER BY sort_order, feature_code
                """, Map.of());
    }

    public List<Map<String, Object>> featureOverrides() {
        return jdbc.queryForList("""
                SELECT o.*, fc.feature_name
                FROM subscription_feature_override o
                JOIN feature_catalog fc ON fc.feature_code=o.feature_code
                ORDER BY o.created_at DESC, o.id DESC
                """, Map.of());
    }

    @Transactional
    public long addFeatureOverride(String featureCode, String overrideType,
                                   LocalDateTime effectiveFrom, LocalDateTime effectiveUntil,
                                   String reason, CurrentUser operator) {
        if (!List.of("GRANT", "REVOKE").contains(overrideType)) {
            throw new BusinessException("FEATURE_OVERRIDE_INVALID", "功能覆盖类型必须为GRANT或REVOKE");
        }
        required(reason);
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        jdbc.update("""
                INSERT INTO subscription_feature_override
                (subscription_id, feature_code, override_type, effective_from,
                 effective_until, change_reason, created_by)
                VALUES (:subscriptionId, :featureCode, :overrideType,
                        :effectiveFrom, :effectiveUntil, :reason, :operatorId)
                """, new MapSqlParameterSource()
                .addValue("subscriptionId", current.subscriptionId())
                .addValue("featureCode", featureCode)
                .addValue("overrideType", overrideType)
                .addValue("effectiveFrom", effectiveFrom == null ? LocalDateTime.now() : effectiveFrom)
                .addValue("effectiveUntil", effectiveUntil)
                .addValue("reason", reason.trim())
                .addValue("operatorId", operator.userId()));
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        auditService.success("GRANT".equals(overrideType) ? "FEATURE_OVERRIDE_ADD" : "FEATURE_OVERRIDE_REMOVE",
                operator.userId(), "SUBSCRIPTION_FEATURE_OVERRIDE", String.valueOf(id), reason,
                null, Map.of("featureCode", featureCode, "overrideType", overrideType));
        return id == null ? 0L : id;
    }

    public List<Map<String, Object>> quotas() {
        return jdbc.queryForList("""
                SELECT quota_code, quota_name, unit_name, enabled_in_program, sort_order
                FROM quota_catalog ORDER BY sort_order, quota_code
                """, Map.of());
    }

    public List<Map<String, Object>> quotaOverrides() {
        return jdbc.queryForList("""
                SELECT o.*, qc.quota_name, qc.unit_name
                FROM subscription_quota_override o
                JOIN quota_catalog qc ON qc.quota_code=o.quota_code
                ORDER BY o.created_at DESC, o.id DESC
                """, Map.of());
    }

    @Transactional
    public long addQuotaOverride(String quotaCode, long quotaValue,
                                 LocalDateTime effectiveFrom, LocalDateTime effectiveUntil,
                                 String reason, CurrentUser operator) {
        if (quotaValue < 0) {
            throw new BusinessException("QUOTA_OVERRIDE_INVALID", "配额值不能为负数");
        }
        required(reason);
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        jdbc.update("""
                INSERT INTO subscription_quota_override
                (subscription_id, quota_code, quota_value, effective_from,
                 effective_until, change_reason, created_by)
                VALUES (:subscriptionId, :quotaCode, :quotaValue,
                        :effectiveFrom, :effectiveUntil, :reason, :operatorId)
                """, new MapSqlParameterSource()
                .addValue("subscriptionId", current.subscriptionId())
                .addValue("quotaCode", quotaCode)
                .addValue("quotaValue", quotaValue)
                .addValue("effectiveFrom", effectiveFrom == null ? LocalDateTime.now() : effectiveFrom)
                .addValue("effectiveUntil", effectiveUntil)
                .addValue("reason", reason.trim())
                .addValue("operatorId", operator.userId()));
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Map.of(), Long.class);
        auditService.success("QUOTA_OVERRIDE_UPDATE", operator.userId(),
                "SUBSCRIPTION_QUOTA_OVERRIDE", String.valueOf(id), reason, null,
                Map.of("quotaCode", quotaCode, "quotaValue", quotaValue));
        return id == null ? 0L : id;
    }

    public List<Map<String, Object>> audit(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return jdbc.queryForList("""
                SELECT id, operation_type, operator_user_id, target_type, target_id,
                       change_reason, before_json, after_json, success, error_code, created_at
                FROM platform_audit_log ORDER BY id DESC LIMIT :limit
                """, Map.of("limit", safeLimit));
    }

    private void required(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("CHANGE_REASON_REQUIRED", "必须填写变更原因");
        }
    }
}
