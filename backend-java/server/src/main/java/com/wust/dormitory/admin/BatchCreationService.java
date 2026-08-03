package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchCreationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final BatchRuleTemplateService batchRuleTemplateService;
    private final FeatureAccessService featureAccessService;
    private final AuditService auditService;

    public BatchCreationService(
            NamedParameterJdbcTemplate jdbc,
            BatchRuleTemplateService batchRuleTemplateService,
            FeatureAccessService featureAccessService,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.batchRuleTemplateService = batchRuleTemplateService;
        this.featureAccessService = featureAccessService;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> create(CreateCommand command, CurrentUser operator) {
        CreateCommand normalized = command.normalized();
        validate(normalized);
        if ("BED".equals(normalized.selectionMode())) {
            featureAccessService.require(FeatureCodes.P2_BED_SELECTION_MODE);
        }

        Map<String, Object> questionnaire = one("""
                SELECT id FROM questionnaire_version
                WHERE version_status='PUBLISHED'
                ORDER BY published_at DESC, id DESC
                LIMIT 1
                """, Map.of(), "QUESTIONNAIRE_REQUIRED", "请先发布个人偏好版本");
        Map<String, Object> scheme = one("""
                SELECT id FROM matching_weight_scheme
                WHERE enabled=1
                ORDER BY published_at DESC, id DESC
                LIMIT 1
                """, Map.of(), "WEIGHT_SCHEME_REQUIRED", "请先配置启用的匹配权重方案");
        BatchRuleTemplateService.RuleSnapshot snapshot =
                batchRuleTemplateService.resolveForBatch(normalized.ruleTemplateId());

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update("""
                    INSERT INTO selection_batch
                    (batch_code, batch_name, batch_status, selection_mode,
                     separate_student_categories,
                     questionnaire_version_id, matching_weight_scheme_id, rule_template_id,
                     start_at, end_at, hold_duration_seconds, hold_renewal_limit,
                     allow_team, team_min_size, team_max_size,
                     allow_student_random, unselected_strategy, rule_version, created_by)
                    VALUES
                    (:batchCode, :batchName, 'DRAFT', :selectionMode,
                     :separateStudentCategories,
                     :questionnaireId, :schemeId, :ruleTemplateId,
                     :startAt, :endAt, :holdDurationSeconds, :holdRenewalLimit,
                     :allowTeam, :teamMinSize, :teamMaxSize,
                     :allowStudentRandom, :unselectedStrategy, :ruleVersion, :createdBy)
                    """, new MapSqlParameterSource()
                    .addValue("batchCode", normalized.batchCode())
                    .addValue("batchName", normalized.batchName())
                    .addValue("selectionMode", normalized.selectionMode())
                    .addValue("separateStudentCategories", normalized.separateStudentCategories() ? 1 : 0)
                    .addValue("questionnaireId", questionnaire.get("id"))
                    .addValue("schemeId", scheme.get("id"))
                    .addValue("ruleTemplateId", snapshot.id())
                    .addValue("startAt", normalized.startAt())
                    .addValue("endAt", normalized.endAt())
                    .addValue("holdDurationSeconds", snapshot.holdDurationSeconds())
                    .addValue("holdRenewalLimit", snapshot.holdRenewalLimit())
                    .addValue("allowTeam", snapshot.allowTeam() ? 1 : 0)
                    .addValue("teamMinSize", snapshot.teamMinSize())
                    .addValue("teamMaxSize", snapshot.teamMaxSize())
                    .addValue("allowStudentRandom", snapshot.allowStudentRandom() ? 1 : 0)
                    .addValue("unselectedStrategy", snapshot.unselectedStrategy())
                    .addValue("ruleVersion", snapshot.ruleVersion())
                    .addValue("createdBy", operator.userId()),
                    keyHolder,
                    new String[]{"id"});
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "BATCH_CODE_CONFLICT",
                    "批次编码已存在，请更换后重试",
                    HttpStatus.CONFLICT);
        }

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("批次创建成功但没有返回批次编号");
        }
        long batchId = key.longValue();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", batchId);
        result.put("batchStatus", "DRAFT");
        result.put("selectionMode", normalized.selectionMode());
        result.put("separateStudentCategories", normalized.separateStudentCategories());
        result.put("ruleTemplateId", snapshot.id());
        result.put("ruleTemplateCode", snapshot.code());
        result.put("ruleTemplateRevision", snapshot.revision());
        result.put("holdDurationSeconds", snapshot.holdDurationSeconds());
        result.put("holdRenewalLimit", snapshot.holdRenewalLimit());
        result.put("allowTeam", snapshot.allowTeam());
        result.put("teamMinSize", snapshot.teamMinSize());
        result.put("teamMaxSize", snapshot.teamMaxSize());
        result.put("allowStudentRandom", snapshot.allowStudentRandom());
        result.put("unselectedStrategy", snapshot.unselectedStrategy());
        result.put("ruleVersion", snapshot.ruleVersion());

        auditService.success(
                operator,
                "BATCH_CREATE",
                "SELECTION_BATCH",
                batchId,
                "使用批次规则模板创建草稿批次",
                null,
                result);
        return result;
    }

    private void validate(CreateCommand command) {
        if (command.batchCode().isBlank()) {
            throw new BusinessException("BATCH_CODE_REQUIRED", "请填写批次编码");
        }
        if (command.batchCode().length() > 32) {
            throw new BusinessException("BATCH_CODE_INVALID", "批次编码不能超过32个字符");
        }
        if (command.batchName().isBlank()) {
            throw new BusinessException("BATCH_NAME_REQUIRED", "请填写批次名称");
        }
        if (command.batchName().length() > 128) {
            throw new BusinessException("BATCH_NAME_INVALID", "批次名称不能超过128个字符");
        }
        if (command.startAt() == null || command.endAt() == null) {
            throw new BusinessException("BATCH_TIME_REQUIRED", "选寝开始时间和结束时间不能为空");
        }
        if (!command.startAt().isBefore(command.endAt())) {
            throw new BusinessException("BATCH_TIME_INVALID", "选寝开始时间必须早于结束时间");
        }
        if (!List.of("ROOM", "BED").contains(command.selectionMode())) {
            throw new BusinessException("BATCH_SELECTION_MODE_INVALID", "批次选择模式必须为选寝室或选床位");
        }
    }

    private Map<String, Object> one(
            String sql,
            Map<String, ?> parameters,
            String code,
            String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message);
        }
        return rows.getFirst();
    }

    public record CreateCommand(
            String batchCode,
            String batchName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            Long ruleTemplateId,
            String selectionMode,
            boolean separateStudentCategories) {

        CreateCommand normalized() {
            return new CreateCommand(
                    batchCode == null ? "" : batchCode.trim(),
                    batchName == null ? "" : batchName.trim(),
                    startAt,
                    endAt,
                    ruleTemplateId,
                    selectionMode == null ? "ROOM" : selectionMode.trim().toUpperCase(),
                    separateStudentCategories);
        }
    }
}
