package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class FeatureAccessService {
    private final NamedParameterJdbcTemplate jdbc;
    private final SubscriptionService subscriptionService;
    private final EntitlementSnapshotService snapshotService;

    public FeatureAccessService(NamedParameterJdbcTemplate jdbc,
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
            throw new BusinessException("SERVICE_EMERGENCY_STOPPED", "系统服务已紧急停止", HttpStatus.SERVICE_UNAVAILABLE);
        }
        boolean currentlyGranted = currentFeatures().contains(featureCode);
        if (mode == AccessMode.READ_EXISTING) {
            if (!currentlyGranted && !implemented(featureCode)) {
                throw new BusinessException("FEATURE_NOT_ENABLED", "该功能当前未开放", HttpStatus.FORBIDDEN);
            }
            return;
        }
        if (mode == AccessMode.CONTINUE_EXISTING_BATCH && batchId != null
                && snapshotService.allowsBatchContinuation(batchId, featureCode)) {
            return;
        }
        if (!subscription.allowsNewOperations()) {
            throw serviceStateException(subscription.serviceStatus());
        }
        if (!currentlyGranted) {
            throw new BusinessException("FEATURE_NOT_ENABLED", "该功能当前未开放", HttpStatus.FORBIDDEN);
        }
    }

    public boolean has(String featureCode) {
        return currentFeatures().contains(featureCode);
    }

    public Set<String> currentFeatures() {
        SubscriptionService.CurrentSubscription subscription = subscriptionService.currentSubscription();
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
                """, Map.of("subscriptionId", subscription.subscriptionId(), "now", LocalDateTime.now()));
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

    private boolean implemented(String featureCode) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM feature_catalog
                WHERE feature_code=:code AND enabled_in_program=1
                """, Map.of("code", featureCode), Integer.class);
        return count != null && count == 1;
    }

    private BusinessException serviceStateException(String status) {
        return switch (status) {
            case "SUSPENDED" -> new BusinessException("SERVICE_SUSPENDED", "当前服务已暂停，历史数据仍可查看", HttpStatus.FORBIDDEN);
            case "EXPIRED" -> new BusinessException("SERVICE_EXPIRED", "当前服务已到期，历史数据仍可查看", HttpStatus.FORBIDDEN);
            case "TERMINATED" -> new BusinessException("SERVICE_TERMINATED", "当前服务已终止，历史数据仍可查看", HttpStatus.FORBIDDEN);
            default -> new BusinessException("SERVICE_OPERATION_NOT_ALLOWED", "当前服务状态不允许执行该操作", HttpStatus.FORBIDDEN);
        };
    }
}
