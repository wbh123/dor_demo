package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PlanService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PlatformAuditService auditService;

    public PlanService(NamedParameterJdbcTemplate jdbc, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> listPlans() {
        return jdbc.queryForList("""
                SELECT p.id, p.plan_code, p.plan_name, p.enabled,
                       pr.id AS latest_revision_id, pr.revision AS latest_revision,
                       pr.revision_name, pr.enabled AS revision_enabled
                FROM subscription_plan p
                LEFT JOIN subscription_plan_revision pr ON pr.id=(
                    SELECT pr2.id FROM subscription_plan_revision pr2
                    WHERE pr2.plan_id=p.id ORDER BY pr2.revision DESC LIMIT 1
                )
                ORDER BY p.created_at, p.id
                """, Map.of());
    }

    public Map<String, Object> revision(long revisionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT pr.*, p.plan_code, p.plan_name
                FROM subscription_plan_revision pr
                JOIN subscription_plan p ON p.id=pr.plan_id
                WHERE pr.id=:id
                """, Map.of("id", revisionId));
        if (rows.isEmpty()) {
            throw new BusinessException("PLAN_REVISION_NOT_FOUND", "套餐修订不存在");
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>(rows.getFirst());
        result.put("features", jdbc.queryForList("""
                SELECT feature_code FROM plan_revision_feature
                WHERE plan_revision_id=:id ORDER BY feature_code
                """, Map.of("id", revisionId), String.class));
        result.put("quotas", jdbc.queryForList("""
                SELECT quota_code, quota_value FROM plan_revision_quota
                WHERE plan_revision_id=:id ORDER BY quota_code
                """, Map.of("id", revisionId)));
        return result;
    }

    @Transactional
    public long createPlan(String code, String name, String revisionName, String description,
                           List<String> features, Map<String, Long> quotas,
                           String reason, CurrentUser operator) {
        required(code, "套餐编码");
        required(name, "套餐名称");
        required(revisionName, "修订名称");
        required(reason, "变更原因");
        jdbc.update("""
                INSERT INTO subscription_plan (plan_code, plan_name, enabled, created_by)
                VALUES (:code, :name, 1, :operatorId)
                """, new MapSqlParameterSource()
                .addValue("code", code.trim())
                .addValue("name", name.trim())
                .addValue("operatorId", operator.userId()));
        Long planId = jdbc.queryForObject("SELECT id FROM subscription_plan WHERE plan_code=:code",
                Map.of("code", code.trim()), Long.class);
        long revisionId = insertRevision(planId, 1, revisionName, description,
                features, quotas, reason, operator.userId());
        auditService.success("PLAN_CREATE", operator.userId(), "SUBSCRIPTION_PLAN",
                String.valueOf(planId), reason, null,
                Map.of("planCode", code, "revisionId", revisionId));
        return revisionId;
    }

    @Transactional
    public long createRevision(long sourceRevisionId, String revisionName, String description,
                               List<String> features, Map<String, Long> quotas,
                               String reason, CurrentUser operator) {
        Map<String, Object> source = revision(sourceRevisionId);
        long planId = ((Number) source.get("plan_id")).longValue();
        jdbc.queryForList("SELECT id FROM subscription_plan WHERE id=:id FOR UPDATE", Map.of("id", planId));
        Integer next = jdbc.queryForObject("""
                SELECT COALESCE(MAX(revision),0)+1 FROM subscription_plan_revision WHERE plan_id=:planId
                """, Map.of("planId", planId), Integer.class);
        long revisionId = insertRevision(planId, next == null ? 1 : next, revisionName, description,
                features, quotas, reason, operator.userId());
        auditService.success("PLAN_REVISE", operator.userId(), "SUBSCRIPTION_PLAN",
                String.valueOf(planId), reason, Map.of("sourceRevisionId", sourceRevisionId),
                Map.of("revisionId", revisionId));
        return revisionId;
    }

    private long insertRevision(long planId, int revision, String revisionName, String description,
                                List<String> features, Map<String, Long> quotas,
                                String reason, long operatorId) {
        required(revisionName, "修订名称");
        required(reason, "变更原因");
        jdbc.update("""
                INSERT INTO subscription_plan_revision
                (plan_id, revision, revision_name, description, enabled, change_reason, created_by)
                VALUES (:planId, :revision, :revisionName, :description, 1, :reason, :operatorId)
                """, new MapSqlParameterSource()
                .addValue("planId", planId).addValue("revision", revision)
                .addValue("revisionName", revisionName.trim()).addValue("description", description)
                .addValue("reason", reason.trim()).addValue("operatorId", operatorId));
        Long revisionId = jdbc.queryForObject("""
                SELECT id FROM subscription_plan_revision WHERE plan_id=:planId AND revision=:revision
                """, Map.of("planId", planId, "revision", revision), Long.class);
        if (revisionId == null) {
            throw new IllegalStateException("套餐修订写入失败");
        }
        if (features != null) {
            for (String feature : features.stream().distinct().toList()) {
                jdbc.update("""
                        INSERT INTO plan_revision_feature (plan_revision_id, feature_code)
                        VALUES (:revisionId, :feature)
                        """, Map.of("revisionId", revisionId, "feature", feature));
            }
        }
        if (quotas != null) {
            quotas.forEach((code, value) -> {
                if (value == null || value < 0) {
                    throw new BusinessException("QUOTA_VALUE_INVALID", "配额值不能为负数");
                }
                jdbc.update("""
                        INSERT INTO plan_revision_quota (plan_revision_id, quota_code, quota_value)
                        VALUES (:revisionId, :code, :value)
                        """, Map.of("revisionId", revisionId, "code", code, "value", value));
            });
        }
        return revisionId;
    }

    private void required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("VALIDATION_ERROR", label + "不能为空");
        }
    }
}
