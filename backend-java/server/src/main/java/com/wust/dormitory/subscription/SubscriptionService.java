package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class SubscriptionService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public SubscriptionService(NamedParameterJdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public CurrentSubscription currentSubscription() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT sr.id, sr.subscription_id, sr.revision, sr.plan_revision_id,
                       sr.subscription_type, sr.service_status, sr.contract_number,
                       sr.start_at, sr.end_at, sr.emergency_stopped, sr.created_at,
                       p.plan_code, p.plan_name, pr.revision AS plan_revision
                FROM service_subscription_revision sr
                JOIN subscription_plan_revision pr ON pr.id=sr.plan_revision_id
                JOIN subscription_plan p ON p.id=pr.plan_id
                WHERE sr.is_current=1
                """, Map.of());
        if (rows.size() != 1) {
            throw new BusinessException("SUBSCRIPTION_NOT_FOUND", "当前服务订阅不存在或状态异常", HttpStatus.SERVICE_UNAVAILABLE);
        }
        Map<String, Object> row = rows.getFirst();
        String type = String.valueOf(row.get("subscription_type"));
        String status = effectiveStatus(type, String.valueOf(row.get("service_status")),
                (LocalDateTime) row.get("end_at"));
        return new CurrentSubscription(
                number(row, "id"), number(row, "subscription_id"),
                ((Number) row.get("revision")).intValue(), number(row, "plan_revision_id"),
                String.valueOf(row.get("plan_code")), String.valueOf(row.get("plan_name")),
                ((Number) row.get("plan_revision")).intValue(), type, status,
                row.get("contract_number") == null ? null : String.valueOf(row.get("contract_number")),
                (LocalDateTime) row.get("start_at"), (LocalDateTime) row.get("end_at"),
                ((Number) row.get("emergency_stopped")).intValue() == 1
        );
    }

    private String effectiveStatus(String type, String storedStatus, LocalDateTime endAt) {
        if (!"LONG_TERM".equals(type) && endAt != null && !endAt.isAfter(LocalDateTime.now())
                && !"TERMINATED".equals(storedStatus)) {
            return "EXPIRED";
        }
        return storedStatus;
    }

    public List<Map<String, Object>> history() {
        return jdbc.queryForList("""
                SELECT sr.*, p.plan_code, p.plan_name, pr.revision AS plan_revision
                FROM service_subscription_revision sr
                JOIN subscription_plan_revision pr ON pr.id=sr.plan_revision_id
                JOIN subscription_plan p ON p.id=pr.plan_id
                ORDER BY sr.revision DESC
                """, Map.of());
    }

    public ChangePreview previewChange(long planRevisionId) {
        CurrentSubscription current = currentSubscription();
        List<String> currentFeatures = featuresForPlanRevision(current.planRevisionId());
        List<String> targetFeatures = featuresForPlanRevision(planRevisionId);
        Map<String, Long> currentQuotas = quotasForPlanRevision(current.planRevisionId());
        Map<String, Long> targetQuotas = quotasForPlanRevision(planRevisionId);
        List<String> added = targetFeatures.stream().filter(code -> !currentFeatures.contains(code)).sorted().toList();
        List<String> removed = currentFeatures.stream().filter(code -> !targetFeatures.contains(code)).sorted().toList();
        return new ChangePreview(current.planRevisionId(), planRevisionId, added, removed, currentQuotas, targetQuotas);
    }

    @Transactional
    public CurrentSubscription changePlan(long planRevisionId, String operationType,
                                          String reason, String contractNumber, CurrentUser operator) {
        CurrentSubscription current = lockCurrent();
        ensurePlanRevisionUsable(planRevisionId);
        Map<String, Object> before = Map.of(
                "subscriptionRevisionId", current.revisionId(),
                "planRevisionId", current.planRevisionId(),
                "serviceStatus", current.serviceStatus()
        );
        long nextRevision = current.revision() + 1L;
        jdbc.update("UPDATE service_subscription_revision SET is_current=0 WHERE id=:id",
                Map.of("id", current.revisionId()));
        jdbc.update("""
                INSERT INTO service_subscription_revision
                (subscription_id, revision, plan_revision_id, subscription_type, service_status,
                 contract_number, start_at, end_at, signed_at, emergency_stopped,
                 change_reason, remark, is_current, created_by)
                SELECT subscription_id, :revision, :planRevisionId, subscription_type, service_status,
                       COALESCE(:contractNumber, contract_number), start_at, end_at, signed_at,
                       emergency_stopped, :reason, remark, 1, :operatorId
                FROM service_subscription_revision WHERE id=:sourceId
                """, new MapSqlParameterSource()
                .addValue("revision", nextRevision)
                .addValue("planRevisionId", planRevisionId)
                .addValue("contractNumber", contractNumber)
                .addValue("reason", requiredReason(reason))
                .addValue("operatorId", operator.userId())
                .addValue("sourceId", current.revisionId()));
        CurrentSubscription changed = currentSubscription();
        auditService.success(operationType, operator.userId(), "SERVICE_SUBSCRIPTION",
                String.valueOf(current.subscriptionId()), reason, before,
                Map.of("subscriptionRevisionId", changed.revisionId(), "planRevisionId", planRevisionId));
        return changed;
    }

    @Transactional
    public CurrentSubscription changeStatus(String status, boolean emergencyStopped,
                                            String operationType, String reason, CurrentUser operator) {
        if (!List.of("TRIAL", "ACTIVE", "SUSPENDED", "EXPIRED", "TERMINATED").contains(status)) {
            throw new BusinessException("SUBSCRIPTION_STATUS_INVALID", "订阅状态不合法");
        }
        CurrentSubscription current = lockCurrent();
        jdbc.update("UPDATE service_subscription_revision SET is_current=0 WHERE id=:id",
                Map.of("id", current.revisionId()));
        jdbc.update("""
                INSERT INTO service_subscription_revision
                (subscription_id, revision, plan_revision_id, subscription_type, service_status,
                 contract_number, start_at, end_at, signed_at, emergency_stopped,
                 change_reason, remark, is_current, created_by)
                SELECT subscription_id, revision+1, plan_revision_id, subscription_type, :status,
                       contract_number, start_at, end_at, signed_at, :emergencyStopped,
                       :reason, remark, 1, :operatorId
                FROM service_subscription_revision WHERE id=:sourceId
                """, new MapSqlParameterSource()
                .addValue("status", status)
                .addValue("emergencyStopped", emergencyStopped ? 1 : 0)
                .addValue("reason", requiredReason(reason))
                .addValue("operatorId", operator.userId())
                .addValue("sourceId", current.revisionId()));
        CurrentSubscription changed = currentSubscription();
        auditService.success(operationType, operator.userId(), "SERVICE_SUBSCRIPTION",
                String.valueOf(current.subscriptionId()), reason,
                Map.of("status", current.serviceStatus(), "emergencyStopped", current.emergencyStopped()),
                Map.of("status", changed.serviceStatus(), "emergencyStopped", changed.emergencyStopped()));
        return changed;
    }

    private CurrentSubscription lockCurrent() {
        jdbc.queryForList("SELECT id FROM service_subscription_revision WHERE is_current=1 FOR UPDATE", Map.of());
        return currentSubscription();
    }

    private void ensurePlanRevisionUsable(long planRevisionId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM subscription_plan_revision
                WHERE id=:id AND enabled=1
                """, Map.of("id", planRevisionId), Integer.class);
        if (count == null || count != 1) {
            throw new BusinessException("PLAN_REVISION_DISABLED", "目标套餐修订不存在或已停用");
        }
    }

    public List<String> featuresForPlanRevision(long planRevisionId) {
        return jdbc.queryForList("""
                SELECT pf.feature_code
                FROM plan_revision_feature pf
                JOIN feature_catalog fc ON fc.feature_code=pf.feature_code
                WHERE pf.plan_revision_id=:id AND fc.enabled_in_program=1
                ORDER BY pf.feature_code
                """, Map.of("id", planRevisionId), String.class);
    }

    public Map<String, Long> quotasForPlanRevision(long planRevisionId) {
        return jdbc.query("""
                SELECT quota_code, quota_value FROM plan_revision_quota
                WHERE plan_revision_id=:id
                """, Map.of("id", planRevisionId), rs -> {
            java.util.LinkedHashMap<String, Long> result = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                result.put(rs.getString(1), rs.getLong(2));
            }
            return result;
        });
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("CHANGE_REASON_REQUIRED", "必须填写变更原因");
        }
        return reason.trim();
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public record CurrentSubscription(
            long revisionId, long subscriptionId, int revision, long planRevisionId,
            String planCode, String planName, int planRevision,
            String subscriptionType, String serviceStatus, String contractNumber,
            LocalDateTime startAt, LocalDateTime endAt, boolean emergencyStopped
    ) {
        public boolean allowsNewOperations() {
            return ("ACTIVE".equals(serviceStatus) || "TRIAL".equals(serviceStatus)) && !emergencyStopped;
        }
    }

    public record ChangePreview(
            long currentPlanRevisionId, long targetPlanRevisionId,
            List<String> addedFeatures, List<String> removedFeatures,
            Map<String, Long> currentQuotas, Map<String, Long> targetQuotas
    ) {
    }
}
