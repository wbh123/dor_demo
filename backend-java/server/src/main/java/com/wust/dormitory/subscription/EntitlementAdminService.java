package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    public List<FeatureEntitlementView> featureEntitlements(boolean includeFuture) {
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        return queryFeatureEntitlements(current.subscriptionId(), current.planRevisionId(), includeFuture, null);
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
    public FeatureEntitlementView setFeatureState(String featureCode,
                                                  FeatureTargetState targetState,
                                                  String reason,
                                                  CurrentUser operator) {
        required(reason);
        FeatureEntitlementView before = findFeatureEntitlement(featureCode, true);
        boolean changed = applyFeatureState(featureCode, targetState, reason, operator.userId());
        FeatureEntitlementView after = findFeatureEntitlement(featureCode, true);
        if (changed) {
            auditService.success("FEATURE_STATE_SET", operator.userId(), "FEATURE_CATALOG",
                    featureCode, reason,
                    Map.of("effectiveEnabled", before.effectiveEnabled(), "source", before.source()),
                    Map.of("effectiveEnabled", after.effectiveEnabled(), "source", after.source(),
                            "targetState", targetState.name()));
        }
        return after;
    }

    @Transactional
    public List<FeatureEntitlementView> setFeatureStates(List<FeatureStateChange> changes,
                                                         String reason,
                                                         CurrentUser operator) {
        required(reason);
        if (changes == null || changes.isEmpty()) {
            throw new BusinessException("FEATURE_BATCH_EMPTY", "至少选择一项功能变更");
        }
        Set<String> distinctCodes = new LinkedHashSet<>();
        for (FeatureStateChange change : changes) {
            if (change == null || change.featureCode() == null || change.featureCode().isBlank()
                    || change.targetState() == null) {
                throw new BusinessException("FEATURE_BATCH_INVALID", "批量功能变更包含无效项目");
            }
            if (!distinctCodes.add(change.featureCode())) {
                throw new BusinessException("FEATURE_BATCH_DUPLICATE", "同一功能不能在一次批量操作中重复出现");
            }
        }

        List<FeatureEntitlementView> before = new ArrayList<>();
        List<FeatureEntitlementView> after = new ArrayList<>();
        for (FeatureStateChange change : changes) {
            before.add(findFeatureEntitlement(change.featureCode(), true));
            applyFeatureState(change.featureCode(), change.targetState(), reason, operator.userId());
            after.add(findFeatureEntitlement(change.featureCode(), true));
        }

        auditService.success("FEATURE_BATCH_STATE_SET", operator.userId(), "SERVICE_SUBSCRIPTION",
                String.valueOf(subscriptionService.currentSubscription().subscriptionId()), reason,
                Map.of("features", before), Map.of("features", after));
        return after;
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

    private boolean applyFeatureState(String featureCode,
                                      FeatureTargetState targetState,
                                      String reason,
                                      long operatorId) {
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        FeatureDefinition definition = lockFeatureDefinition(featureCode, current.planRevisionId());
        if (!definition.enabledInProgram()) {
            throw new BusinessException("FEATURE_NOT_IMPLEMENTED", "该功能尚未在当前程序版本中实现，不能授权");
        }

        List<Map<String, Object>> activeOverrides = jdbc.queryForList("""
                SELECT id, override_type
                FROM subscription_feature_override
                WHERE subscription_id=:subscriptionId
                  AND feature_code=:featureCode
                  AND effective_from <= :now
                  AND (effective_until IS NULL OR effective_until > :now)
                ORDER BY created_at DESC, id DESC
                FOR UPDATE
                """, Map.of(
                "subscriptionId", current.subscriptionId(),
                "featureCode", featureCode,
                "now", LocalDateTime.now()
        ));

        String requiredOverride = requiredOverride(definition.planEnabled(), targetState);
        if (requiredOverride == null && activeOverrides.isEmpty()) {
            return false;
        }
        if (requiredOverride != null && activeOverrides.size() == 1
                && requiredOverride.equals(String.valueOf(activeOverrides.getFirst().get("override_type")))) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("""
                UPDATE subscription_feature_override
                SET effective_until = CASE
                    WHEN effective_from >= :now THEN DATE_ADD(effective_from, INTERVAL 1000 MICROSECOND)
                    ELSE :now
                END
                WHERE subscription_id=:subscriptionId
                  AND feature_code=:featureCode
                  AND effective_from <= :now
                  AND (effective_until IS NULL OR effective_until > :now)
                """, Map.of(
                "subscriptionId", current.subscriptionId(),
                "featureCode", featureCode,
                "now", now
        ));

        if (requiredOverride != null) {
            jdbc.update("""
                    INSERT INTO subscription_feature_override
                    (subscription_id, feature_code, override_type, effective_from,
                     effective_until, change_reason, created_by)
                    VALUES (:subscriptionId, :featureCode, :overrideType,
                            :effectiveFrom, NULL, :reason, :operatorId)
                    """, new MapSqlParameterSource()
                    .addValue("subscriptionId", current.subscriptionId())
                    .addValue("featureCode", featureCode)
                    .addValue("overrideType", requiredOverride)
                    .addValue("effectiveFrom", now)
                    .addValue("reason", reason.trim())
                    .addValue("operatorId", operatorId));
        }
        return true;
    }

    private String requiredOverride(boolean planEnabled, FeatureTargetState targetState) {
        if (targetState == FeatureTargetState.INHERIT) {
            return null;
        }
        boolean targetEnabled = targetState == FeatureTargetState.ENABLED;
        if (targetEnabled == planEnabled) {
            return null;
        }
        return targetEnabled ? "GRANT" : "REVOKE";
    }

    private FeatureDefinition lockFeatureDefinition(String featureCode, long planRevisionId) {
        List<FeatureDefinition> definitions = jdbc.query("""
                SELECT fc.feature_code, fc.enabled_in_program,
                       CASE WHEN prf.feature_code IS NULL THEN 0 ELSE 1 END AS plan_enabled
                FROM feature_catalog fc
                LEFT JOIN plan_revision_feature prf
                  ON prf.plan_revision_id=:planRevisionId
                 AND prf.feature_code=fc.feature_code
                WHERE fc.feature_code=:featureCode
                FOR UPDATE
                """, Map.of("planRevisionId", planRevisionId, "featureCode", featureCode),
                (rs, rowNum) -> new FeatureDefinition(
                        rs.getString("feature_code"),
                        rs.getInt("enabled_in_program") == 1,
                        rs.getInt("plan_enabled") == 1
                ));
        if (definitions.isEmpty()) {
            throw new BusinessException("FEATURE_NOT_FOUND", "功能代码不存在");
        }
        return definitions.getFirst();
    }

    private FeatureEntitlementView findFeatureEntitlement(String featureCode, boolean includeFuture) {
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        List<FeatureEntitlementView> rows = queryFeatureEntitlements(
                current.subscriptionId(), current.planRevisionId(), includeFuture, featureCode);
        if (rows.isEmpty()) {
            throw new BusinessException("FEATURE_NOT_FOUND", "功能代码不存在");
        }
        return rows.getFirst();
    }

    private List<FeatureEntitlementView> queryFeatureEntitlements(long subscriptionId,
                                                                  long planRevisionId,
                                                                  boolean includeFuture,
                                                                  String featureCode) {
        LocalDateTime now = LocalDateTime.now();
        return jdbc.query("""
                SELECT fc.feature_code, fc.feature_name, fc.phase, fc.scope,
                       fc.granularity, fc.action_type, fc.risk_level,
                       fc.enabled_in_program, fc.sort_order,
                       CASE WHEN prf.feature_code IS NULL THEN 0 ELSE 1 END AS plan_enabled,
                       active_override.override_type,
                       active_override.created_at AS override_created_at
                FROM feature_catalog fc
                LEFT JOIN plan_revision_feature prf
                  ON prf.plan_revision_id=:planRevisionId
                 AND prf.feature_code=fc.feature_code
                LEFT JOIN subscription_feature_override active_override
                  ON active_override.id=(
                    SELECT o.id
                    FROM subscription_feature_override o
                    WHERE o.subscription_id=:subscriptionId
                      AND o.feature_code=fc.feature_code
                      AND o.effective_from <= :now
                      AND (o.effective_until IS NULL OR o.effective_until > :now)
                    ORDER BY o.created_at DESC, o.id DESC
                    LIMIT 1
                  )
                WHERE (:includeFuture=1 OR fc.enabled_in_program=1)
                  AND (:featureCode IS NULL OR fc.feature_code=:featureCode)
                ORDER BY fc.sort_order, fc.feature_code
                """, new MapSqlParameterSource()
                .addValue("subscriptionId", subscriptionId)
                .addValue("planRevisionId", planRevisionId)
                .addValue("now", now)
                .addValue("includeFuture", includeFuture ? 1 : 0)
                .addValue("featureCode", featureCode),
                (rs, rowNum) -> {
                    boolean planEnabled = rs.getInt("plan_enabled") == 1;
                    String overrideType = rs.getString("override_type");
                    boolean effectiveEnabled = switch (overrideType == null ? "" : overrideType) {
                        case "GRANT" -> true;
                        case "REVOKE" -> false;
                        default -> planEnabled;
                    };
                    String source = switch (overrideType == null ? "" : overrideType) {
                        case "GRANT" -> "OVERRIDE_GRANT";
                        case "REVOKE" -> "OVERRIDE_REVOKE";
                        default -> planEnabled ? "PLAN_ENABLED" : "PLAN_DISABLED";
                    };
                    Timestamp timestamp = rs.getTimestamp("override_created_at");
                    return new FeatureEntitlementView(
                            rs.getString("feature_code"),
                            rs.getString("feature_name"),
                            rs.getString("phase"),
                            rs.getString("scope"),
                            rs.getString("granularity"),
                            rs.getString("action_type"),
                            rs.getString("risk_level"),
                            rs.getInt("enabled_in_program") == 1,
                            rs.getInt("sort_order"),
                            planEnabled,
                            effectiveEnabled,
                            overrideType,
                            source,
                            timestamp == null ? null : timestamp.toLocalDateTime()
                    );
                });
    }

    private void required(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("CHANGE_REASON_REQUIRED", "必须填写变更原因");
        }
    }

    public enum FeatureTargetState {
        ENABLED,
        DISABLED,
        INHERIT
    }

    public record FeatureStateChange(String featureCode, FeatureTargetState targetState) {
    }

    public record FeatureEntitlementView(
            String featureCode,
            String featureName,
            String phase,
            String scope,
            String granularity,
            String actionType,
            String riskLevel,
            boolean enabledInProgram,
            int sortOrder,
            boolean planEnabled,
            boolean effectiveEnabled,
            String overrideType,
            String source,
            LocalDateTime lastChangedAt
    ) {
    }

    private record FeatureDefinition(String featureCode, boolean enabledInProgram, boolean planEnabled) {
    }
}
