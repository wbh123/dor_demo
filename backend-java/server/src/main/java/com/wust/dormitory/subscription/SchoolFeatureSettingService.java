package com.wust.dormitory.subscription;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SchoolFeatureSettingService {
    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureAccessService featureAccessService;
    private final AuditService auditService;

    public SchoolFeatureSettingService(
            NamedParameterJdbcTemplate jdbc,
            FeatureAccessService featureAccessService,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.featureAccessService = featureAccessService;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list() {
        Map<String, FeatureAccessEvaluator.State> states = featureAccessService.currentFeatureStates();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT fc.feature_code,
                       fc.feature_name,
                       fc.category_code,
                       category.category_name,
                       fc.enabled_in_program,
                       fc.school_controllable,
                       fc.school_default_enabled,
                       fc.risk_level,
                       school.enabled AS school_setting_enabled,
                       school.version,
                       school.change_reason,
                       school.updated_at,
                       updater.display_name AS updated_by_name
                FROM feature_catalog fc
                JOIN feature_category category
                  ON category.category_code=fc.category_code
                LEFT JOIN school_feature_setting school
                  ON school.feature_code=fc.feature_code
                LEFT JOIN app_user updater
                  ON updater.id=school.updated_by
                ORDER BY category.sort_order, fc.sort_order, fc.feature_code
                """, Map.of());

        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String featureCode = String.valueOf(row.get("feature_code"));
            FeatureAccessEvaluator.State state = states.get(featureCode);
            if (state == null) {
                continue;
            }
            Map<String, Object> view = new LinkedHashMap<>(row);
            view.put("systemGranted", state.systemGranted());
            view.put("schoolEnabled", state.schoolEnabled());
            view.put("effectiveEnabled", state.effectiveEnabled());
            view.put("unavailableReason", state.unavailableReason());
            view.put("version", row.get("version") == null ? 0 : row.get("version"));
            view.remove("school_setting_enabled");
            result.add(view);
        }
        return result;
    }

    @Transactional
    public Map<String, Object> update(
            String featureCode,
            boolean enabled,
            int expectedVersion,
            String reason,
            boolean highRiskConfirmed,
            CurrentUser operator) {
        CurrentState before = currentStateForUpdate(featureCode);
        validateChange(before, enabled, expectedVersion, reason, highRiskConfirmed);

        int nextVersion;
        if (before.version() == 0) {
            try {
                jdbc.update("""
                        INSERT INTO school_feature_setting
                        (feature_code, enabled, version, change_reason,
                         updated_by, updated_at)
                        VALUES
                        (:featureCode, :enabled, 1, :reason,
                         :operatorId, CURRENT_TIMESTAMP(3))
                        """, new MapSqlParameterSource()
                        .addValue("featureCode", featureCode)
                        .addValue("enabled", enabled ? 1 : 0)
                        .addValue("reason", reason.trim())
                        .addValue("operatorId", operator.userId()));
                nextVersion = 1;
            } catch (DuplicateKeyException exception) {
                throw versionConflict();
            }
        } else {
            int changed = jdbc.update("""
                    UPDATE school_feature_setting
                    SET enabled=:enabled,
                        version=version+1,
                        change_reason=:reason,
                        updated_by=:operatorId,
                        updated_at=CURRENT_TIMESTAMP(3)
                    WHERE feature_code=:featureCode
                      AND version=:expectedVersion
                    """, new MapSqlParameterSource()
                    .addValue("featureCode", featureCode)
                    .addValue("enabled", enabled ? 1 : 0)
                    .addValue("reason", reason.trim())
                    .addValue("operatorId", operator.userId())
                    .addValue("expectedVersion", expectedVersion));
            if (changed != 1) {
                throw versionConflict();
            }
            nextVersion = expectedVersion + 1;
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("featureCode", featureCode);
        after.put("enabled", enabled);
        after.put("version", nextVersion);
        after.put("reason", reason.trim());
        auditService.success(
                operator,
                "SCHOOL_FEATURE_SETTING_UPDATE",
                "SCHOOL_FEATURE_SETTING",
                featureCode,
                reason.trim(),
                auditView(before),
                after);
        return after;
    }

    private CurrentState currentStateForUpdate(String featureCode) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT fc.feature_code,
                       fc.enabled_in_program,
                       fc.school_controllable,
                       fc.school_default_enabled,
                       fc.risk_level,
                       school.enabled AS school_setting_enabled,
                       school.version
                FROM feature_catalog fc
                LEFT JOIN school_feature_setting school
                  ON school.feature_code=fc.feature_code
                WHERE fc.feature_code=:featureCode
                """, Map.of("featureCode", featureCode));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "FEATURE_NOT_FOUND",
                    "功能目录项不存在",
                    HttpStatus.NOT_FOUND);
        }
        Map<String, Object> row = rows.getFirst();
        FeatureAccessEvaluator.State projected =
                featureAccessService.currentFeatureStates().get(featureCode);
        if (projected == null) {
            throw new BusinessException(
                    "FEATURE_NOT_FOUND",
                    "功能目录项不存在",
                    HttpStatus.NOT_FOUND);
        }
        int version = row.get("version") == null
                ? 0
                : ((Number) row.get("version")).intValue();
        boolean schoolEnabled = row.get("school_setting_enabled") == null
                ? FeatureAccessService.booleanValue(row.get("school_default_enabled"))
                : FeatureAccessService.booleanValue(row.get("school_setting_enabled"));
        return new CurrentState(
                featureCode,
                projected.enabledInProgram(),
                projected.systemGranted(),
                FeatureAccessService.booleanValue(row.get("school_controllable")),
                FeatureAccessService.booleanValue(row.get("school_default_enabled")),
                String.valueOf(row.getOrDefault("risk_level", "LOW")),
                version,
                schoolEnabled);
    }

    static void validateChange(
            CurrentState current,
            boolean enabled,
            int expectedVersion,
            String reason,
            boolean highRiskConfirmed) {
        if (!current.schoolControllable()) {
            throw new BusinessException(
                    "FEATURE_NOT_SCHOOL_CONTROLLABLE",
                    "该功能由系统统一控制，学校管理员不能修改",
                    HttpStatus.FORBIDDEN);
        }
        if (!current.systemGranted()) {
            throw new BusinessException(
                    "FEATURE_SYSTEM_NOT_GRANTED",
                    "系统管理员尚未授权该功能，学校不能修改其启用状态",
                    HttpStatus.FORBIDDEN);
        }
        if (enabled && !current.enabledInProgram()) {
            throw new BusinessException(
                    "FEATURE_NOT_IMPLEMENTED",
                    "该功能程序尚未实现，不能启用",
                    HttpStatus.FORBIDDEN);
        }
        if (expectedVersion != current.version()) {
            throw versionConflict();
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException(
                    "SCHOOL_FEATURE_CHANGE_REASON_REQUIRED",
                    "必须填写不超过500个字符的修改原因");
        }
        if ("HIGH".equalsIgnoreCase(current.riskLevel()) && !highRiskConfirmed) {
            throw new BusinessException(
                    "SCHOOL_FEATURE_HIGH_RISK_CONFIRMATION_REQUIRED",
                    "该功能风险较高，请二次确认后再修改",
                    HttpStatus.CONFLICT);
        }
    }

    private static BusinessException versionConflict() {
        return new BusinessException(
                "SCHOOL_FEATURE_SETTING_VERSION_CONFLICT",
                "功能设置已经被其他管理员修改，请重新加载后再保存",
                HttpStatus.CONFLICT);
    }

    private Map<String, Object> auditView(CurrentState state) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("featureCode", state.featureCode());
        result.put("enabled", state.schoolEnabled());
        result.put("version", state.version());
        result.put("systemGranted", state.systemGranted());
        result.put("enabledInProgram", state.enabledInProgram());
        return result;
    }

    public record CurrentState(
            String featureCode,
            boolean enabledInProgram,
            boolean systemGranted,
            boolean schoolControllable,
            boolean schoolDefaultEnabled,
            String riskLevel,
            int version,
            boolean schoolEnabled) {
    }
}
