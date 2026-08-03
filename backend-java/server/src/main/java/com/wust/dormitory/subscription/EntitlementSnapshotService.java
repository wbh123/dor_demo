package com.wust.dormitory.subscription;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class EntitlementSnapshotService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final FeatureAccessService featureAccessService;
    private final QuotaService quotaService;
    private final SubscriptionService subscriptionService;

    public EntitlementSnapshotService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
                                      @Lazy FeatureAccessService featureAccessService,
                                      @Lazy QuotaService quotaService,
                                      SubscriptionService subscriptionService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.featureAccessService = featureAccessService;
        this.quotaService = quotaService;
        this.subscriptionService = subscriptionService;
    }

    public void captureForBatch(long batchId) {
        SubscriptionService.CurrentSubscription subscription = subscriptionService.currentSubscription();
        if (!subscription.allowsNewOperations()) {
            throw new BusinessException("SERVICE_OPERATION_NOT_ALLOWED", "当前服务状态不允许启动新的选寝活动");
        }
        String featuresJson = json(featureAccessService.currentFeatures());
        String quotasJson = json(quotaService.currentQuotas());
        jdbc.update("""
                INSERT INTO batch_entitlement_snapshot
                (batch_id, subscription_revision_id, granted_features_json,
                 quota_snapshot_json, snapshot_version)
                VALUES (:batchId, :subscriptionRevisionId,
                        CAST(:features AS JSON), CAST(:quotas AS JSON), 'ENTITLEMENT_V1')
                ON DUPLICATE KEY UPDATE batch_id=batch_id
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("subscriptionRevisionId", subscription.revisionId())
                .addValue("features", featuresJson)
                .addValue("quotas", quotasJson));
    }

    public boolean allowsBatchContinuation(long batchId, String featureCode) {
        List<String> snapshots = jdbc.queryForList("""
                SELECT granted_features_json FROM batch_entitlement_snapshot
                WHERE batch_id=:batchId
                """, Map.of("batchId", batchId), String.class);
        if (snapshots.isEmpty()) {
            return false;
        }
        try {
            Set<String> features = objectMapper.readValue(snapshots.getFirst(), new TypeReference<>() { });
            return features.contains(featureCode);
        } catch (Exception exception) {
            throw new BusinessException("BATCH_ENTITLEMENT_SNAPSHOT_INVALID", "批次权限快照无法读取");
        }
    }

    public Map<String, Object> snapshot(long batchId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT batch_id, subscription_revision_id, granted_features_json,
                       quota_snapshot_json, snapshot_version, captured_at
                FROM batch_entitlement_snapshot WHERE batch_id=:batchId
                """, Map.of("batchId", batchId));
        if (rows.isEmpty()) {
            throw new BusinessException("BATCH_ENTITLEMENT_SNAPSHOT_MISSING", "该批次尚未生成启动权限快照");
        }
        return rows.getFirst();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("权限快照序列化失败", exception);
        }
    }
}
