package com.wust.dormitory.retention;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import com.wust.dormitory.subscription.QuotaCodes;
import com.wust.dormitory.subscription.SubscriptionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataRetentionQueryService {
    public static final String CURRENT_STUDENT = "CURRENT_STUDENT";
    public static final String ACTIVE_RESIDENCY = "ACTIVE_RESIDENCY";
    public static final String ACTIVE_BATCH = "ACTIVE_BATCH";
    public static final String PENDING_ROOM_CHANGE = "PENDING_ROOM_CHANGE";
    public static final String PENDING_EXCHANGE = "PENDING_EXCHANGE";
    public static final String PENDING_WAITLIST = "PENDING_WAITLIST";
    public static final String ACTIVE_ENTITLEMENT = "ACTIVE_ENTITLEMENT";
    public static final String PENDING_EXPORT = "PENDING_EXPORT";
    public static final String LEGAL_AUDIT_HOLD = "LEGAL_AUDIT_HOLD";

    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureAccessService featureAccessService;
    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    public DataRetentionQueryService(
            NamedParameterJdbcTemplate jdbc,
            FeatureAccessService featureAccessService,
            SubscriptionService subscriptionService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.featureAccessService = featureAccessService;
        this.subscriptionService = subscriptionService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> policy() {
        featureAccessService.require(FeatureCodes.P3_DATA_RETENTION_QUERY);
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        Map<String, Long> quotas = subscriptionService.quotasForPlanRevision(current.planRevisionId());
        long dataDays = quotas.getOrDefault(QuotaCodes.DATA_RETENTION_DAYS, 1095L);
        long auditDays = quotas.getOrDefault(QuotaCodes.AUDIT_RETENTION_DAYS, 2190L);
        return Map.of(
                "dataRetentionDays", dataDays,
                "auditRetentionDays", auditDays,
                "executionEnabled", false,
                "protectedReasons", protectedReasons(),
                "note", "本轮仅展示策略、到期统计、模拟清理和预检，不执行生产清理");
    }

    public Map<String, Object> expiringStatistics() {
        featureAccessService.require(FeatureCodes.P3_DATA_RETENTION_QUERY);
        Map<String, Object> policy = policy();
        long dataDays = ((Number) policy.get("dataRetentionDays")).longValue();
        long auditDays = ((Number) policy.get("auditRetentionDays")).longValue();
        LocalDateTime dataCutoff = LocalDateTime.now().minusDays(dataDays);
        LocalDateTime auditCutoff = LocalDateTime.now().minusDays(auditDays);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("dataCutoff", dataCutoff)
                .addValue("auditCutoff", auditCutoff);
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("finishedExportTasks", scalar("""
                SELECT COUNT(*) FROM export_task
                WHERE task_status IN ('SUCCEEDED','FAILED','CANCELLED')
                  AND completed_at<:dataCutoff
                """, params));
        counts.put("completedNotificationTasks", scalar("""
                SELECT COUNT(*) FROM notification_send_task
                WHERE task_status IN ('SUCCEEDED','FAILED','CANCELLED')
                  AND completed_at<:dataCutoff
                """, params));
        counts.put("expiredOperationalAnomalies", scalar("""
                SELECT COUNT(*) FROM operation_anomaly
                WHERE resolved_at IS NOT NULL AND resolved_at<:dataCutoff
                """, params));
        counts.put("expiredAuditRows", scalar("""
                SELECT COUNT(*) FROM audit_log
                WHERE occurred_at<:auditCutoff
                """, params));
        return Map.of(
                "cutoffs", Map.of(
                        "dataCutoff", dataCutoff.toString(),
                        "auditCutoff", auditCutoff.toString()),
                "counts", counts);
    }

    public Map<String, Object> simulate() {
        featureAccessService.require(FeatureCodes.P3_DATA_RETENTION_QUERY);
        Map<String, Object> statistics = expiringStatistics();
        Map<String, Long> protectedCounts = protectedCounts();
        return Map.of(
                "mode", "SIMULATION",
                "statistics", statistics,
                "protectedCounts", protectedCounts,
                "wouldExecute", false,
                "requiresSeparateProductionApproval", true);
    }

    public Map<String, Object> preflight(CurrentUser operator, String reason) {
        featureAccessService.require(FeatureCodes.P3_DATA_RETENTION_QUERY);
        String normalizedReason = requireReason(reason);
        Map<String, Object> policy = policy();
        Map<String, Object> simulation = simulate();
        jdbc.update("""
                INSERT INTO data_retention_preflight
                (policy_snapshot_json, simulation_json, requested_by,
                 request_reason, execution_allowed)
                VALUES
                (CAST(:policy AS JSON),CAST(:simulation AS JSON),
                 :requestedBy,:reason,0)
                """, new MapSqlParameterSource()
                .addValue("policy", json(policy))
                .addValue("simulation", json(simulation))
                .addValue("requestedBy", operator.userId())
                .addValue("reason", normalizedReason));
        return Map.of(
                "preflight", simulation,
                "executionAllowed", false,
                "protectedReasons", protectedReasons());
    }

    private Map<String, Long> protectedCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(CURRENT_STUDENT, scalar("SELECT COUNT(*) FROM student WHERE student_status='ACTIVE'", new MapSqlParameterSource()));
        counts.put(ACTIVE_RESIDENCY, scalar("SELECT COUNT(*) FROM room_assignment WHERE assignment_status='ACTIVE'", new MapSqlParameterSource()));
        counts.put(ACTIVE_BATCH, scalar("SELECT COUNT(*) FROM selection_batch WHERE batch_status IN ('DRAFT','PUBLISHED','OPEN','PAUSED','CLOSED','ALLOCATING')", new MapSqlParameterSource()));
        counts.put(PENDING_ROOM_CHANGE, scalar("SELECT COUNT(*) FROM room_change_request WHERE request_status NOT IN ('EXECUTED','REJECTED','CANCELLED')", new MapSqlParameterSource()));
        counts.put(PENDING_EXCHANGE, scalar("SELECT COUNT(*) FROM room_exchange_request WHERE exchange_status NOT IN ('EXECUTED','REJECTED','CANCELLED')", new MapSqlParameterSource()));
        counts.put(PENDING_WAITLIST, scalar("SELECT COUNT(*) FROM waitlist_entry WHERE entry_status IN ('WAITING','OFFERED')", new MapSqlParameterSource()));
        counts.put(ACTIVE_ENTITLEMENT, scalar("SELECT COUNT(*) FROM service_subscription_revision WHERE is_current=1", new MapSqlParameterSource()));
        counts.put(PENDING_EXPORT, scalar("SELECT COUNT(*) FROM export_task WHERE task_status IN ('QUEUED','RUNNING')", new MapSqlParameterSource()));
        counts.put(LEGAL_AUDIT_HOLD, scalar("SELECT COUNT(*) FROM audit_retention_hold WHERE active=1", new MapSqlParameterSource()));
        return counts;
    }

    private List<Map<String, String>> protectedReasons() {
        return List.of(
                reason(CURRENT_STUDENT, "当前学生不可清理"),
                reason(ACTIVE_RESIDENCY, "正式在住记录不可清理"),
                reason(ACTIVE_BATCH, "活动批次不可清理"),
                reason(PENDING_ROOM_CHANGE, "未完成换寝不可清理"),
                reason(PENDING_EXCHANGE, "未完成交换不可清理"),
                reason(PENDING_WAITLIST, "未完成候补不可清理"),
                reason(ACTIVE_ENTITLEMENT, "有效授权不可清理"),
                reason(PENDING_EXPORT, "未完成导出不可清理"),
                reason(LEGAL_AUDIT_HOLD, "依法或业务保留的审计不可清理"));
    }

    private Map<String, String> reason(String code, String description) {
        return Map.of("code", code, "description", description);
    }

    private long scalar(String sql, MapSqlParameterSource parameters) {
        Number value = jdbc.queryForObject(sql, parameters, Number.class);
        return value == null ? 0L : value.longValue();
    }

    private String requireReason(String reason) {
        if (reason == null || reason.trim().length() < 2) {
            throw new BusinessException("RETENTION_PREFLIGHT_REASON_REQUIRED", "记录清理预检必须填写原因");
        }
        return reason.trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("数据保留预检无法序列化", exception);
        }
    }
}
