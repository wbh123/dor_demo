package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FeatureAccessService {
    private final NamedParameterJdbcTemplate jdbc;
    private final SubscriptionService subscriptionService;
    private final EntitlementSnapshotService snapshotService;

    public FeatureAccessService(
            NamedParameterJdbcTemplate jdbc,
            SubscriptionService subscriptionService,
            EntitlementSnapshotService snapshotService) {
        this.jdbc = jdbc;
        this.subscriptionService = subscriptionService;
        this.snapshotService = snapshotService;
    }

    public void require(String featureCode) {
        require(featureCode, AccessMode.START_NEW, null);
    }

    public void require(String featureCode, AccessMode mode, Long batchId) {
        SubscriptionService.CurrentSubscription subscription = subscriptionService.currentSubscription();
        if (subscription.emergencyStopped()) {
            throw new BusinessException(
                    "SERVICE_EMERGENCY_STOPPED",
                    "系统服务已紧急停止",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        FeatureAccessEvaluator.State state = currentFeatureStates().get(featureCode);
        if (mode == AccessMode.READ_EXISTING) {
            if (state == null || !state.enabledInProgram()) {
                throw new BusinessException(
                        "FEATURE_NOT_ENABLED",
                        "该功能当前未开放",
                        HttpStatus.FORBIDDEN);
            }
            return;
        }
        if (mode == AccessMode.CONTINUE_EXISTING_BATCH
                && batchId != null
                && snapshotService.allowsBatchContinuation(batchId, featureCode)) {
            return;
        }
        if (!subscription.allowsNewOperations()) {
            throw serviceStateException(subscription.serviceStatus());
        }
        if (state == null || !state.effectiveEnabled()) {
            throw featureStateException(state);
        }
    }

    public boolean has(String featureCode) {
        FeatureAccessEvaluator.State state = currentFeatureStates().get(featureCode);
        return state != null && state.effectiveEnabled();
    }

    /**
     * 普通业务页面使用的最终有效功能集合。
     */
    public Set<String> currentFeatures() {
        Set<String> result = new LinkedHashSet<>();
        currentFeatureStates().forEach((code, state) -> {
            if (state.effectiveEnabled()) {
                result.add(code);
            }
        });
        return Set.copyOf(result);
    }

    /**
     * 学校功能设置页和认证快照使用的完整三层投影。
     */
    public Map<String, FeatureAccessEvaluator.State> currentFeatureStates() {
        SubscriptionService.CurrentSubscription subscription = subscriptionService.currentSubscription();
        Set<String> systemGranted = systemGrantedFeatures(subscription);
        boolean businessAllowed = subscription.allowsNewOperations();
        List<Map<String, Object>> catalog = jdbc.queryForList("""
                SELECT fc.feature_code,
                       fc.enabled_in_program,
                       fc.school_controllable,
                       fc.school_default_enabled,
                       school.enabled AS school_enabled
                FROM feature_catalog fc
                LEFT JOIN school_feature_setting school
                  ON school.feature_code=fc.feature_code
                ORDER BY fc.category_code, fc.sort_order, fc.feature_code
                """, Map.of());

        Map<String, FeatureAccessEvaluator.State> states = new LinkedHashMap<>();
        for (Map<String, Object> row : catalog) {
            String code = String.valueOf(row.get("feature_code"));
            Boolean schoolSetting = nullableBoolean(row.get("school_enabled"));
            FeatureAccessEvaluator.State state = FeatureAccessEvaluator.evaluate(
                    new FeatureAccessEvaluator.Input(
                            booleanValue(row.get("enabled_in_program")),
                            systemGranted.contains(code),
                            booleanValue(row.get("school_controllable")),
                            booleanValue(row.get("school_default_enabled")),
                            schoolSetting,
                            businessAllowed));
            states.put(code, state);
        }
        return Map.copyOf(states);
    }

    private Set<String> systemGrantedFeatures(
            SubscriptionService.CurrentSubscription subscription) {
        Set<String> features = new LinkedHashSet<>(
                subscriptionService.featuresForPlanRevision(subscription.planRevisionId()));
        List<Map<String, Object>> overrides = jdbc.queryForList("""
                SELECT o.feature_code, o.override_type
                FROM subscription_feature_override o
                JOIN feature_catalog fc ON fc.feature_code=o.feature_code
                WHERE o.subscription_id=:subscriptionId
                  AND o.effective_from <= :now
                  AND (o.effective_until IS NULL OR o.effective_until > :now)
                  AND fc.enabled_in_program=1
                ORDER BY o.created_at, o.id
                """, Map.of(
                "subscriptionId", subscription.subscriptionId(),
                "now", LocalDateTime.now()));
        for (Map<String, Object> override : overrides) {
            String code = String.valueOf(override.get("feature_code"));
            if ("GRANT".equals(String.valueOf(override.get("override_type")))) {
                features.add(code);
            } else {
                features.remove(code);
            }
        }
        return Set.copyOf(features);
    }

    private BusinessException featureStateException(FeatureAccessEvaluator.State state) {
        if (state == null || "SYSTEM_NOT_GRANTED".equals(state.unavailableReason())) {
            return new BusinessException(
                    "FEATURE_SYSTEM_NOT_GRANTED",
                    "系统管理员尚未授权该功能",
                    HttpStatus.FORBIDDEN);
        }
        return switch (state.unavailableReason()) {
            case "NOT_IMPLEMENTED" -> new BusinessException(
                    "FEATURE_NOT_IMPLEMENTED",
                    "该功能程序尚未实现",
                    HttpStatus.FORBIDDEN);
            case "SCHOOL_DISABLED" -> new BusinessException(
                    "FEATURE_SCHOOL_DISABLED",
                    "该功能已由学校管理员关闭",
                    HttpStatus.FORBIDDEN);
            case "BUSINESS_STATE_BLOCKED" -> new BusinessException(
                    "FEATURE_BUSINESS_STATE_BLOCKED",
                    "当前业务状态暂不可使用该功能",
                    HttpStatus.FORBIDDEN);
            default -> new BusinessException(
                    "FEATURE_NOT_ENABLED",
                    "该功能当前未开放",
                    HttpStatus.FORBIDDEN);
        };
    }

    static boolean booleanValue(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return value != null && ("1".equals(String.valueOf(value))
                || Boolean.parseBoolean(String.valueOf(value)));
    }

    static Boolean nullableBoolean(Object value) {
        return value == null ? null : booleanValue(value);
    }

    private BusinessException serviceStateException(String status) {
        return switch (status) {
            case "SUSPENDED" -> new BusinessException(
                    "SERVICE_SUSPENDED",
                    "当前服务已暂停，历史数据仍可查看",
                    HttpStatus.FORBIDDEN);
            case "EXPIRED" -> new BusinessException(
                    "SERVICE_EXPIRED",
                    "当前服务已到期，历史数据仍可查看",
                    HttpStatus.FORBIDDEN);
            case "TERMINATED" -> new BusinessException(
                    "SERVICE_TERMINATED",
                    "当前服务已终止，历史数据仍可查看",
                    HttpStatus.FORBIDDEN);
            default -> new BusinessException(
                    "SERVICE_OPERATION_NOT_ALLOWED",
                    "当前服务状态不允许执行该操作",
                    HttpStatus.FORBIDDEN);
        };
    }
}
