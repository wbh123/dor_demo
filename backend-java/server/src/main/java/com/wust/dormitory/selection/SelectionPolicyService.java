package com.wust.dormitory.selection;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.student.StudentPreferenceService;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SelectionPolicyService {
    public static final String QUESTIONNAIRE_BYPASS = "ALLOW_SELECTION_WITHOUT_QUESTIONNAIRE";
    public static final String STUDENT_RESELECT = "ALLOW_STUDENT_RESELECT";
    public static final String ALLOW_DIRECT_PREFERENCE_WITHOUT_BATCH = "ALLOW_DIRECT_PREFERENCE_WITHOUT_BATCH";
    private final NamedParameterJdbcTemplate jdbc;
    private final StudentPreferenceService preferenceService;
    private final FeatureAccessService featureAccessService;
    private final AuditService auditService;

    public SelectionPolicyService(NamedParameterJdbcTemplate jdbc, StudentPreferenceService preferenceService,
                                  FeatureAccessService featureAccessService, AuditService auditService) {
        this.jdbc = jdbc;
        this.preferenceService = preferenceService;
        this.featureAccessService = featureAccessService;
        this.auditService = auditService;
    }

    public Map<String, Object> policy() {
        ensure();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allowWithoutQuestionnaire", enabled(QUESTIONNAIRE_BYPASS));
        result.put("allowStudentReselect", enabled(STUDENT_RESELECT));
        result.put("directPreferenceWithoutBatchAllowed", directPreferenceWithoutBatchAllowed());
        result.put("questionnaireBypassFeatureEnabled", featureAccessService.has(FeatureCodes.P2_QUESTIONNAIRE_BYPASS_CONTROL));
        result.put("studentReselectFeatureEnabled", featureAccessService.has(FeatureCodes.P2_STUDENT_RESELECT_CONTROL));
        result.put("version", policyVersion());
        return result;
    }

    @Transactional
    public Map<String, Object> update(boolean allowWithoutQuestionnaire,
                                      boolean allowStudentReselect,
                                      boolean directPreferenceWithoutBatchAllowed,
                                      String reason,
                                      CurrentUser operator) {
        if (allowWithoutQuestionnaire && !featureAccessService.has(FeatureCodes.P2_QUESTIONNAIRE_BYPASS_CONTROL)) {
            throw new BusinessException("FEATURE_NOT_ENABLED", "系统管理员尚未开放未填写问卷直接选寝功能", HttpStatus.FORBIDDEN);
        }
        if (allowStudentReselect && !featureAccessService.has(FeatureCodes.P2_STUDENT_RESELECT_CONTROL)) {
            throw new BusinessException("FEATURE_NOT_ENABLED", "系统管理员尚未开放学生自主取消重选功能", HttpStatus.FORBIDDEN);
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("POLICY_REASON_REQUIRED", "请填写策略修改原因");
        }
        Map<String, Object> before = policy();
        write(QUESTIONNAIRE_BYPASS, allowWithoutQuestionnaire);
        write(STUDENT_RESELECT, allowStudentReselect);
        write(ALLOW_DIRECT_PREFERENCE_WITHOUT_BATCH, directPreferenceWithoutBatchAllowed);
        Map<String, Object> after = policy();
        auditService.success(operator, "SELECTION_POLICY_UPDATE", "SYSTEM_SETTING", null,
                reason.trim(), before, after);
        return after;
    }

    public boolean directPreferenceWithoutBatchAllowed() {
        return enabled(ALLOW_DIRECT_PREFERENCE_WITHOUT_BATCH);
    }

    public Map<String, Object> readiness(long batchId, long studentId) {
        boolean completed = preferenceService.completed(studentId) || hasBatchFeature(batchId, studentId);
        boolean bypass = enabled(QUESTIONNAIRE_BYPASS)
                && featureAccessService.has(FeatureCodes.P2_QUESTIONNAIRE_BYPASS_CONTROL);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("preferenceCompleted", completed);
        result.put("selectionAllowed", completed || bypass);
        result.put("requiresConfirmation", !completed && bypass);
        result.put("allowWithoutQuestionnaire", bypass);
        result.put("allowStudentReselect", canStudentReselect());
        result.put("directPreferenceWithoutBatchAllowed", directPreferenceWithoutBatchAllowed());
        result.put("message", completed ? "个人偏好已完成"
                : bypass ? "你尚未填写偏好，继续选寝将降低推荐准确性"
                : "请先填写个人偏好后再选寝");
        return result;
    }

    public void requireSelectionReady(long batchId, long studentId) {
        Map<String, Object> state = readiness(batchId, studentId);
        if (!Boolean.TRUE.equals(state.get("selectionAllowed"))) {
            throw new BusinessException("PREFERENCE_REQUIRED", String.valueOf(state.get("message")), HttpStatus.CONFLICT);
        }
    }

    public boolean canStudentReselect() {
        return enabled(STUDENT_RESELECT)
                && featureAccessService.has(FeatureCodes.P2_STUDENT_RESELECT_CONTROL);
    }

    @Transactional
    public Map<String, Object> cancelAssignment(long batchId, long studentId, CurrentUser user) {
        if (!canStudentReselect()) {
            throw new BusinessException("STUDENT_RESELECT_DISABLED", "管理员当前未开放自主取消重选", HttpStatus.FORBIDDEN);
        }
        Integer open = jdbc.queryForObject(
                "SELECT COUNT(*) FROM selection_batch WHERE id=:batchId AND batch_status='OPEN'",
                Map.of("batchId", batchId), Integer.class);
        if (open == null || open == 0) {
            throw new BusinessException("BATCH_NOT_OPEN", "只有开放中的批次可以取消重选", HttpStatus.CONFLICT);
        }
        int bed = jdbc.update("""
                UPDATE bed_assignment SET assignment_status='CANCELLED'
                WHERE batch_id=:batchId AND student_id=:studentId AND assignment_status='ACTIVE'
                """, Map.of("batchId", batchId, "studentId", studentId));
        int room = jdbc.update("""
                UPDATE room_assignment SET assignment_status='ENDED', ended_at=CURRENT_TIMESTAMP(3),
                    end_reason='学生自主取消重选', updated_at=CURRENT_TIMESTAMP(3)
                WHERE batch_id=:batchId AND student_id=:studentId AND assignment_status='ACTIVE'
                """, Map.of("batchId", batchId, "studentId", studentId));
        if (bed + room == 0) {
            throw new BusinessException("ASSIGNMENT_NOT_FOUND", "当前批次没有可取消的选寝结果", HttpStatus.NOT_FOUND);
        }
        auditService.success(user, "STUDENT_ASSIGNMENT_CANCEL", "SELECTION_BATCH", batchId,
                "学生自主取消并重选", null, Map.of("bedAssignments", bed, "roomAssignments", room));
        return Map.of("cancelled", true, "batchId", batchId);
    }

    private boolean hasBatchFeature(long batchId, long studentId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student_feature WHERE batch_id=:batchId AND student_id=:studentId",
                Map.of("batchId", batchId, "studentId", studentId), Integer.class);
        return count != null && count > 0;
    }

    private int policyVersion() {
        Integer version = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version),0) FROM system_setting WHERE setting_key IN (:keys)",
                Map.of("keys", List.of(QUESTIONNAIRE_BYPASS, STUDENT_RESELECT,
                        ALLOW_DIRECT_PREFERENCE_WITHOUT_BATCH)), Integer.class);
        return version == null ? 0 : version;
    }

    private boolean enabled(String key) {
        ensure();
        List<String> rows = jdbc.query("SELECT setting_value FROM system_setting WHERE setting_key=:key",
                Map.of("key", key), (rs, n) -> rs.getString(1));
        return !rows.isEmpty() && Boolean.parseBoolean(rows.getFirst());
    }

    private void write(String key, boolean value) {
        int updated = jdbc.update(
                "UPDATE system_setting SET setting_value=:value,version=version+1 WHERE setting_key=:key",
                Map.of("key", key, "value", Boolean.toString(value)));
        if (updated == 0) {
            ensure(key, value);
        }
    }

    private void ensure() {
        ensure(QUESTIONNAIRE_BYPASS, false);
        ensure(STUDENT_RESELECT, false);
        ensure(ALLOW_DIRECT_PREFERENCE_WITHOUT_BATCH, true);
    }

    private void ensure(String key, boolean defaultValue) {
        jdbc.update("""
                INSERT INTO system_setting(setting_key,setting_value,version)
                VALUES (:key,:value,0)
                ON DUPLICATE KEY UPDATE setting_key=VALUES(setting_key)
                """, Map.of("key", key, "value", Boolean.toString(defaultValue)));
    }
}
