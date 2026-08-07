package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.mapper.EntitlementAdminMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EntitlementAdminService {
    private final EntitlementAdminMapper mapper;
    private final SubscriptionService subscriptionService;
    private final PlatformAuditService auditService;

    public EntitlementAdminService(
            EntitlementAdminMapper mapper,
            SubscriptionService subscriptionService,
            PlatformAuditService auditService) {
        this.mapper = mapper;
        this.subscriptionService = subscriptionService;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> features() { return mapper.findFeatures(); }

    public List<FeatureEntitlementView> featureEntitlements(boolean includeFuture) {
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        return queryFeatureEntitlements(current.subscriptionId(), current.planRevisionId(), includeFuture, null);
    }

    public List<Map<String, Object>> featureOverrides() { return mapper.findFeatureOverrides(); }

    @Transactional
    public FeatureEntitlementView setFeatureState(
            String featureCode, FeatureTargetState targetState, String reason, CurrentUser operator) {
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
    public List<FeatureEntitlementView> setFeatureStates(
            List<FeatureStateChange> changes, String reason, CurrentUser operator) {
        required(reason);
        validateChanges(changes);
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
    public long addFeatureOverride(
            String featureCode, String overrideType, LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil, String reason, CurrentUser operator) {
        if (!List.of("GRANT", "REVOKE").contains(overrideType)) {
            throw new BusinessException("FEATURE_OVERRIDE_INVALID", "功能覆盖类型必须为GRANT或REVOKE");
        }
        required(reason);
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        Map<String, Object> values = overrideValues(current.subscriptionId(), featureCode, overrideType,
                effectiveFrom, effectiveUntil, reason, operator.userId());
        mapper.insertFeatureOverride(values);
        long id = number(values.get("id"));
        auditService.success("GRANT".equals(overrideType) ? "FEATURE_OVERRIDE_ADD" : "FEATURE_OVERRIDE_REMOVE",
                operator.userId(), "SUBSCRIPTION_FEATURE_OVERRIDE", String.valueOf(id), reason,
                null, Map.of("featureCode", featureCode, "overrideType", overrideType));
        return id;
    }

    public List<Map<String, Object>> quotas() { return mapper.findQuotas(); }

    public List<Map<String, Object>> quotaOverrides() { return mapper.findQuotaOverrides(); }

    @Transactional
    public long addQuotaOverride(
            String quotaCode, long quotaValue, LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil, String reason, CurrentUser operator) {
        if (quotaValue < 0) throw new BusinessException("QUOTA_OVERRIDE_INVALID", "配额值不能为负数");
        required(reason);
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subscriptionId", current.subscriptionId());
        values.put("quotaCode", quotaCode);
        values.put("quotaValue", quotaValue);
        values.put("effectiveFrom", effectiveFrom == null ? LocalDateTime.now() : effectiveFrom);
        values.put("effectiveUntil", effectiveUntil);
        values.put("reason", reason.trim());
        values.put("operatorId", operator.userId());
        mapper.insertQuotaOverride(values);
        long id = number(values.get("id"));
        auditService.success("QUOTA_OVERRIDE_UPDATE", operator.userId(), "SUBSCRIPTION_QUOTA_OVERRIDE",
                String.valueOf(id), reason, null, Map.of("quotaCode", quotaCode, "quotaValue", quotaValue));
        return id;
    }

    public List<Map<String, Object>> audit(int limit) {
        return mapper.findAuditLogs(Math.max(1, Math.min(limit, 500)));
    }

    private boolean applyFeatureState(
            String featureCode, FeatureTargetState targetState, String reason, long operatorId) {
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        FeatureDefinition definition = lockFeatureDefinition(featureCode, current.planRevisionId());
        if (!definition.enabledInProgram()) {
            throw new BusinessException("FEATURE_NOT_IMPLEMENTED", "该功能尚未在当前程序版本中实现，不能授权");
        }
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> active = mapper.findActiveFeatureOverridesForUpdate(
                current.subscriptionId(), featureCode, now);
        String requiredOverride = requiredOverride(definition.planEnabled(), targetState);
        if (requiredOverride == null && active.isEmpty()) return false;
        if (requiredOverride != null && active.size() == 1
                && requiredOverride.equals(String.valueOf(active.getFirst().get("override_type")))) return false;
        mapper.closeActiveFeatureOverrides(current.subscriptionId(), featureCode, now);
        if (requiredOverride != null) {
            mapper.insertFeatureOverride(overrideValues(current.subscriptionId(), featureCode, requiredOverride,
                    now, null, reason, operatorId));
        }
        return true;
    }

    private FeatureDefinition lockFeatureDefinition(String featureCode, long planRevisionId) {
        Map<String, Object> row = mapper.lockFeatureDefinition(featureCode, planRevisionId);
        if (row == null || row.isEmpty()) throw new BusinessException("FEATURE_NOT_FOUND", "功能代码不存在");
        return new FeatureDefinition(String.valueOf(row.get("feature_code")),
                bool(row.get("enabled_in_program")), bool(row.get("plan_enabled")));
    }

    private FeatureEntitlementView findFeatureEntitlement(String featureCode, boolean includeFuture) {
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        List<FeatureEntitlementView> rows = queryFeatureEntitlements(
                current.subscriptionId(), current.planRevisionId(), includeFuture, featureCode);
        if (rows.isEmpty()) throw new BusinessException("FEATURE_NOT_FOUND", "功能代码不存在");
        return rows.getFirst();
    }

    private List<FeatureEntitlementView> queryFeatureEntitlements(
            long subscriptionId, long planRevisionId, boolean includeFuture, String featureCode) {
        return mapper.findFeatureEntitlements(subscriptionId, planRevisionId, LocalDateTime.now(),
                        includeFuture, featureCode).stream().map(this::view).toList();
    }

    private FeatureEntitlementView view(Map<String, Object> row) {
        boolean planEnabled = bool(row.get("plan_enabled"));
        String overrideType = text(row.get("override_type"));
        boolean effectiveEnabled = switch (overrideType) {
            case "GRANT" -> true; case "REVOKE" -> false; default -> planEnabled;
        };
        String source = switch (overrideType) {
            case "GRANT" -> "OVERRIDE_GRANT"; case "REVOKE" -> "OVERRIDE_REVOKE";
            default -> planEnabled ? "PLAN_ENABLED" : "PLAN_DISABLED";
        };
        return new FeatureEntitlementView(text(row.get("feature_code")), text(row.get("feature_name")),
                text(row.get("phase")), text(row.get("scope")), text(row.get("granularity")),
                text(row.get("action_type")), text(row.get("risk_level")), bool(row.get("enabled_in_program")),
                integer(row.get("sort_order")), planEnabled, effectiveEnabled,
                overrideType.isEmpty() ? null : overrideType, source, localDateTime(row.get("override_created_at")));
    }

    private Map<String, Object> overrideValues(
            long subscriptionId, String featureCode, String overrideType, LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil, String reason, long operatorId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("subscriptionId", subscriptionId);
        values.put("featureCode", featureCode);
        values.put("overrideType", overrideType);
        values.put("effectiveFrom", effectiveFrom == null ? LocalDateTime.now() : effectiveFrom);
        values.put("effectiveUntil", effectiveUntil);
        values.put("reason", reason.trim());
        values.put("operatorId", operatorId);
        return values;
    }

    private void validateChanges(List<FeatureStateChange> changes) {
        if (changes == null || changes.isEmpty()) throw new BusinessException("FEATURE_BATCH_EMPTY", "至少选择一项功能变更");
        Set<String> codes = new LinkedHashSet<>();
        for (FeatureStateChange change : changes) {
            if (change == null || change.featureCode() == null || change.featureCode().isBlank()
                    || change.targetState() == null) {
                throw new BusinessException("FEATURE_BATCH_INVALID", "批量功能变更包含无效项目");
            }
            if (!codes.add(change.featureCode())) throw new BusinessException("FEATURE_BATCH_DUPLICATE", "同一功能不能在一次批量操作中重复出现");
        }
    }

    private String requiredOverride(boolean planEnabled, FeatureTargetState targetState) {
        if (targetState == FeatureTargetState.INHERIT) return null;
        boolean targetEnabled = targetState == FeatureTargetState.ENABLED;
        if (targetEnabled == planEnabled) return null;
        return targetEnabled ? "GRANT" : "REVOKE";
    }

    private void required(String reason) {
        if (reason == null || reason.isBlank()) throw new BusinessException("CHANGE_REASON_REQUIRED", "必须填写变更原因");
    }

    private boolean bool(Object value) { return value instanceof Boolean b ? b : value instanceof Number n && n.intValue() == 1; }
    private int integer(Object value) { return value == null ? 0 : ((Number) value).intValue(); }
    private long number(Object value) { return value == null ? 0L : ((Number) value).longValue(); }
    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime time) return time;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return null;
    }

    public enum FeatureTargetState { ENABLED, DISABLED, INHERIT }
    public record FeatureStateChange(String featureCode, FeatureTargetState targetState) { }
    public record FeatureEntitlementView(
            String featureCode, String featureName, String phase, String scope, String granularity,
            String actionType, String riskLevel, boolean enabledInProgram, int sortOrder,
            boolean planEnabled, boolean effectiveEnabled, String overrideType, String source,
            LocalDateTime lastChangedAt) { }
    private record FeatureDefinition(String featureCode, boolean enabledInProgram, boolean planEnabled) { }
}
