package com.wust.dormitory.admin;

import com.wust.dormitory.admin.mapper.BatchCopyMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchCopyService {
    private static final int MAX_RESOURCE_ERRORS = 20;

    private final BatchCopyMapper mapper;
    private final AuditService auditService;
    private final FeatureAccessService featureAccessService;

    public BatchCopyService(
            BatchCopyMapper mapper,
            AuditService auditService,
            FeatureAccessService featureAccessService) {
        this.mapper = mapper;
        this.auditService = auditService;
        this.featureAccessService = featureAccessService;
    }

    @Transactional
    public Map<String, Object> copy(
            long sourceBatchId,
            CopyCommand command,
            CurrentUser operator) {
        CopyCommand normalized = command.normalized();
        validateCommand(normalized);

        Map<String, Object> source = sourceBatchForUpdate(sourceBatchId);
        if ("CANCELLED".equals(String.valueOf(source.get("batch_status")))) {
            throw new BusinessException(
                    "BATCH_COPY_CANCELLED_FORBIDDEN",
                    "已取消的选寝批次不能作为复制来源");
        }
        if ("BED".equals(String.valueOf(source.get("selection_mode")))) {
            featureAccessService.require(FeatureCodes.P2_BED_SELECTION_MODE);
        }

        ensureBatchCodeAvailable(normalized.batchCode());
        validateTemplateReferences(source);

        ScopeCounts scopeCounts = scopeCounts(sourceBatchId);
        if (scopeCounts.total() == 0) {
            throw new BusinessException(
                    "BATCH_COPY_TEMPLATE_INCOMPLETE",
                    "源批次没有配置楼栋、房间或床位开放范围，不能复制");
        }

        List<String> unavailableResources = mapper.findUnavailableResources(
                sourceBatchId, MAX_RESOURCE_ERRORS);
        if (!unavailableResources.isEmpty()) {
            throw new BusinessException(
                    "BATCH_COPY_RESOURCE_UNAVAILABLE",
                    "源批次包含不可用资源：" + String.join("；", unavailableResources),
                    HttpStatus.CONFLICT);
        }

        long newBatchId = insertBatch(source, normalized, operator);
        int copiedBuildings = mapper.copyBuildingScope(sourceBatchId, newBatchId);
        int copiedRooms = mapper.copyRoomScope(sourceBatchId, newBatchId);
        int copiedBeds = mapper.copyBedScope(sourceBatchId, newBatchId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", newBatchId);
        result.put("sourceBatchId", sourceBatchId);
        result.put("batchStatus", "DRAFT");
        result.put("selectionMode", source.get("selection_mode"));
        result.put("separateStudentCategories",
                number(source.get("separate_student_categories")) == 1);
        result.put("ruleTemplateId", source.get("rule_template_id"));
        result.put("buildingScopeCount", copiedBuildings);
        result.put("roomScopeCount", copiedRooms);
        result.put("bedScopeCount", copiedBeds);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("sourceBatchId", sourceBatchId);
        before.put("sourceBatchCode", source.get("batch_code"));
        before.put("sourceBatchName", source.get("batch_name"));
        before.put("sourceBatchStatus", source.get("batch_status"));
        before.put("selectionMode", source.get("selection_mode"));
        before.put("separateStudentCategories",
                number(source.get("separate_student_categories")) == 1);
        before.put("ruleTemplateId", source.get("rule_template_id"));

        auditService.success(
                operator,
                "BATCH_COPY",
                "SELECTION_BATCH",
                newBatchId,
                normalized.reason(),
                before,
                result);
        return result;
    }

    private void validateCommand(CopyCommand command) {
        if (command.batchCode().isBlank()) {
            throw new BusinessException("BATCH_CODE_REQUIRED", "请填写新批次编码");
        }
        if (command.batchName().isBlank()) {
            throw new BusinessException("BATCH_NAME_REQUIRED", "请填写新批次名称");
        }
        if (command.startAt() == null || command.endAt() == null
                || !command.endAt().isAfter(command.startAt())) {
            throw new BusinessException(
                    "BATCH_TIME_INVALID",
                    "新批次结束时间必须晚于开始时间");
        }
        if (command.reason().isBlank()) {
            throw new BusinessException("BATCH_COPY_REASON_REQUIRED", "请填写复制原因");
        }
    }

    private Map<String, Object> sourceBatchForUpdate(long sourceBatchId) {
        Map<String, Object> source = mapper.findSourceBatchForUpdate(sourceBatchId);
        if (source == null || source.isEmpty()) {
            throw new BusinessException(
                    "BATCH_NOT_FOUND",
                    "选寝批次不存在",
                    HttpStatus.NOT_FOUND);
        }
        return source;
    }

    private void ensureBatchCodeAvailable(String batchCode) {
        if (mapper.countBatchCode(batchCode) > 0) {
            throw new BusinessException(
                    "BATCH_CODE_CONFLICT",
                    "批次编码已存在，请更换后重试",
                    HttpStatus.CONFLICT);
        }
    }

    private void validateTemplateReferences(Map<String, Object> source) {
        Object questionnaireId = source.get("questionnaire_version_id");
        Object schemeId = source.get("matching_weight_scheme_id");
        Object ruleTemplateId = source.get("rule_template_id");
        if (questionnaireId == null || schemeId == null || ruleTemplateId == null) {
            throw new BusinessException(
                    "BATCH_COPY_TEMPLATE_INCOMPLETE",
                    "源批次缺少个人偏好版本、匹配方案或批次规则模板，不能复制");
        }
        Map<String, Object> references = mapper.validateTemplateReferences(
                ((Number) questionnaireId).longValue(),
                ((Number) schemeId).longValue(),
                ((Number) ruleTemplateId).longValue());
        if (references == null
                || number(references.get("questionnaire_exists")) != 1
                || number(references.get("scheme_exists")) != 1
                || number(references.get("rule_template_exists")) != 1) {
            throw new BusinessException(
                    "BATCH_COPY_TEMPLATE_INCOMPLETE",
                    "源批次引用的个人偏好版本、匹配方案或批次规则模板已经不存在");
        }
    }

    private ScopeCounts scopeCounts(long batchId) {
        Map<String, Object> counts = mapper.findScopeCounts(batchId);
        return new ScopeCounts(
                number(counts == null ? null : counts.get("building_count")),
                number(counts == null ? null : counts.get("room_count")),
                number(counts == null ? null : counts.get("bed_count")));
    }

    private long insertBatch(
            Map<String, Object> source,
            CopyCommand command,
            CurrentUser operator) {
        Map<String, Object> batch = new LinkedHashMap<>();
        batch.put("batchCode", command.batchCode());
        batch.put("batchName", command.batchName());
        batch.put("selectionMode", source.get("selection_mode"));
        batch.put("separateStudentCategories", source.get("separate_student_categories"));
        batch.put("questionnaireVersionId", source.get("questionnaire_version_id"));
        batch.put("matchingWeightSchemeId", source.get("matching_weight_scheme_id"));
        batch.put("ruleTemplateId", source.get("rule_template_id"));
        batch.put("startAt", command.startAt());
        batch.put("endAt", command.endAt());
        batch.put("holdDurationSeconds", source.get("hold_duration_seconds"));
        batch.put("holdRenewalLimit", source.get("hold_renewal_limit"));
        batch.put("allowTeam", source.get("allow_team"));
        batch.put("teamMinSize", source.get("team_min_size"));
        batch.put("teamMaxSize", source.get("team_max_size"));
        batch.put("allowStudentRandom", source.get("allow_student_random"));
        batch.put("unselectedStrategy", source.get("unselected_strategy"));
        batch.put("ruleVersion", source.get("rule_version"));
        batch.put("createdBy", operator.userId());
        try {
            mapper.insertBatch(batch);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "BATCH_CODE_CONFLICT",
                    "批次编码已存在，请更换后重试",
                    HttpStatus.CONFLICT);
        }
        Object rawId = batch.get("id");
        if (!(rawId instanceof Number id)) {
            throw new IllegalStateException("批次复制成功但没有返回新批次编号");
        }
        return id.longValue();
    }

    private int number(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    public record CopyCommand(
            String batchCode,
            String batchName,
            LocalDateTime startAt,
            LocalDateTime endAt,
            String reason) {

        CopyCommand normalized() {
            return new CopyCommand(
                    batchCode == null ? "" : batchCode.trim(),
                    batchName == null ? "" : batchName.trim(),
                    startAt,
                    endAt,
                    reason == null ? "" : reason.trim());
        }
    }

    private record ScopeCounts(int buildings, int rooms, int beds) {
        int total() {
            return buildings + rooms + beds;
        }
    }
}
