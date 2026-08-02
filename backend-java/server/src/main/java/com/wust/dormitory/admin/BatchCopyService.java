package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BatchCopyService {
    private static final int MAX_RESOURCE_ERRORS = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public BatchCopyService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
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

        ensureBatchCodeAvailable(normalized.batchCode());
        validateTemplateReferences(source);

        ScopeCounts scopeCounts = scopeCounts(sourceBatchId);
        if (scopeCounts.total() == 0) {
            throw new BusinessException(
                    "BATCH_COPY_TEMPLATE_INCOMPLETE",
                    "源批次没有配置楼栋、房间或床位开放范围，不能复制");
        }

        List<String> unavailableResources = unavailableResources(sourceBatchId);
        if (!unavailableResources.isEmpty()) {
            throw new BusinessException(
                    "BATCH_COPY_RESOURCE_UNAVAILABLE",
                    "源批次包含不可用资源：" + String.join("；", unavailableResources),
                    HttpStatus.CONFLICT);
        }

        long newBatchId = insertBatch(source, normalized, operator);
        int copiedBuildings = copyBuildingScope(sourceBatchId, newBatchId);
        int copiedRooms = copyRoomScope(sourceBatchId, newBatchId);
        int copiedBeds = copyBedScope(sourceBatchId, newBatchId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", newBatchId);
        result.put("sourceBatchId", sourceBatchId);
        result.put("batchStatus", "DRAFT");
        result.put("ruleTemplateId", source.get("rule_template_id"));
        result.put("buildingScopeCount", copiedBuildings);
        result.put("roomScopeCount", copiedRooms);
        result.put("bedScopeCount", copiedBeds);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("sourceBatchId", sourceBatchId);
        before.put("sourceBatchCode", source.get("batch_code"));
        before.put("sourceBatchName", source.get("batch_name"));
        before.put("sourceBatchStatus", source.get("batch_status"));
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
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, batch_code, batch_name, batch_status,
                       questionnaire_version_id, matching_weight_scheme_id, rule_template_id,
                       hold_duration_seconds, hold_renewal_limit,
                       allow_team, team_min_size, team_max_size,
                       allow_student_random, unselected_strategy, rule_version
                FROM selection_batch
                WHERE id=:batchId
                FOR UPDATE
                """, Map.of("batchId", sourceBatchId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "BATCH_NOT_FOUND",
                    "选寝批次不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void ensureBatchCodeAvailable(String batchCode) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM selection_batch WHERE batch_code=:batchCode",
                Map.of("batchCode", batchCode),
                Integer.class);
        if (count != null && count > 0) {
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
        int questionnaireCount = count(
                "SELECT COUNT(*) FROM questionnaire_version WHERE id=:id",
                Map.of("id", questionnaireId));
        int schemeCount = count(
                "SELECT COUNT(*) FROM matching_weight_scheme WHERE id=:id",
                Map.of("id", schemeId));
        int ruleTemplateCount = count(
                "SELECT COUNT(*) FROM batch_rule_template WHERE id=:id",
                Map.of("id", ruleTemplateId));
        if (questionnaireCount != 1 || schemeCount != 1 || ruleTemplateCount != 1) {
            throw new BusinessException(
                    "BATCH_COPY_TEMPLATE_INCOMPLETE",
                    "源批次引用的个人偏好版本、匹配方案或批次规则模板已经不存在");
        }
    }

    private ScopeCounts scopeCounts(long batchId) {
        return new ScopeCounts(
                count("SELECT COUNT(*) FROM batch_building_scope WHERE batch_id=:batchId",
                        Map.of("batchId", batchId)),
                count("SELECT COUNT(*) FROM batch_room_scope WHERE batch_id=:batchId",
                        Map.of("batchId", batchId)),
                count("SELECT COUNT(*) FROM batch_bed_scope WHERE batch_id=:batchId",
                        Map.of("batchId", batchId)));
    }

    private List<String> unavailableResources(long batchId) {
        List<String> result = new ArrayList<>();
        result.addAll(jdbc.queryForList("""
                SELECT CONCAT('楼栋', b.building_name, '(', b.building_code, ')未启用')
                FROM batch_building_scope scope
                JOIN dormitory_building b ON b.id=scope.building_id
                WHERE scope.batch_id=:batchId AND b.enabled<>1
                ORDER BY b.building_code
                LIMIT 20
                """, Map.of("batchId", batchId), String.class));

        if (result.size() < MAX_RESOURCE_ERRORS) {
            result.addAll(jdbc.queryForList("""
                    SELECT CONCAT('房间', b.building_name, '-', r.room_number,
                                  '状态为', r.operational_status)
                    FROM batch_room_scope scope
                    JOIN room r ON r.id=scope.room_id
                    JOIN dormitory_floor f ON f.id=r.floor_id
                    JOIN dormitory_building b ON b.id=f.building_id
                    WHERE scope.batch_id=:batchId
                      AND (b.enabled<>1 OR r.operational_status<>'ENABLED')
                    ORDER BY b.building_code, f.floor_number, r.room_number
                    LIMIT 20
                    """, Map.of("batchId", batchId), String.class));
        }

        if (result.size() < MAX_RESOURCE_ERRORS) {
            result.addAll(jdbc.queryForList("""
                    SELECT CONCAT('床位', b.building_name, '-', r.room_number, '-', bed.bed_code,
                                  '状态为', bed.operational_status)
                    FROM batch_bed_scope scope
                    JOIN bed ON bed.id=scope.bed_id
                    JOIN room r ON r.id=bed.room_id
                    JOIN dormitory_floor f ON f.id=r.floor_id
                    JOIN dormitory_building b ON b.id=f.building_id
                    WHERE scope.batch_id=:batchId
                      AND (b.enabled<>1 OR r.operational_status<>'ENABLED'
                           OR bed.operational_status<>'ENABLED')
                    ORDER BY b.building_code, f.floor_number, r.room_number, bed.position_index
                    LIMIT 20
                    """, Map.of("batchId", batchId), String.class));
        }

        if (result.size() > MAX_RESOURCE_ERRORS) {
            return List.copyOf(result.subList(0, MAX_RESOURCE_ERRORS));
        }
        return List.copyOf(result);
    }

    private long insertBatch(
            Map<String, Object> source,
            CopyCommand command,
            CurrentUser operator) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbc.update("""
                    INSERT INTO selection_batch
                    (batch_code, batch_name, batch_status,
                     questionnaire_version_id, matching_weight_scheme_id, rule_template_id,
                     start_at, end_at, hold_duration_seconds, hold_renewal_limit,
                     allow_team, team_min_size, team_max_size,
                     allow_student_random, unselected_strategy, rule_version, created_by)
                    VALUES
                    (:batchCode, :batchName, 'DRAFT',
                     :questionnaireVersionId, :matchingWeightSchemeId, :ruleTemplateId,
                     :startAt, :endAt, :holdDurationSeconds, :holdRenewalLimit,
                     :allowTeam, :teamMinSize, :teamMaxSize,
                     :allowStudentRandom, :unselectedStrategy, :ruleVersion, :createdBy)
                    """, new MapSqlParameterSource()
                    .addValue("batchCode", command.batchCode())
                    .addValue("batchName", command.batchName())
                    .addValue("questionnaireVersionId", source.get("questionnaire_version_id"))
                    .addValue("matchingWeightSchemeId", source.get("matching_weight_scheme_id"))
                    .addValue("ruleTemplateId", source.get("rule_template_id"))
                    .addValue("startAt", command.startAt())
                    .addValue("endAt", command.endAt())
                    .addValue("holdDurationSeconds", source.get("hold_duration_seconds"))
                    .addValue("holdRenewalLimit", source.get("hold_renewal_limit"))
                    .addValue("allowTeam", source.get("allow_team"))
                    .addValue("teamMinSize", source.get("team_min_size"))
                    .addValue("teamMaxSize", source.get("team_max_size"))
                    .addValue("allowStudentRandom", source.get("allow_student_random"))
                    .addValue("unselectedStrategy", source.get("unselected_strategy"))
                    .addValue("ruleVersion", source.get("rule_version"))
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
            throw new IllegalStateException("批次复制成功但没有返回新批次编号");
        }
        return key.longValue();
    }

    private int copyBuildingScope(long sourceBatchId, long newBatchId) {
        return jdbc.update("""
                INSERT INTO batch_building_scope (batch_id, building_id)
                SELECT :newBatchId, building_id
                FROM batch_building_scope
                WHERE batch_id=:sourceBatchId
                """, Map.of(
                "sourceBatchId", sourceBatchId,
                "newBatchId", newBatchId));
    }

    private int copyRoomScope(long sourceBatchId, long newBatchId) {
        return jdbc.update("""
                INSERT INTO batch_room_scope (batch_id, room_id)
                SELECT :newBatchId, room_id
                FROM batch_room_scope
                WHERE batch_id=:sourceBatchId
                """, Map.of(
                "sourceBatchId", sourceBatchId,
                "newBatchId", newBatchId));
    }

    private int copyBedScope(long sourceBatchId, long newBatchId) {
        return jdbc.update("""
                INSERT INTO batch_bed_scope (batch_id, bed_id)
                SELECT :newBatchId, bed_id
                FROM batch_bed_scope
                WHERE batch_id=:sourceBatchId
                """, Map.of(
                "sourceBatchId", sourceBatchId,
                "newBatchId", newBatchId));
    }

    private int count(String sql, Map<String, ?> parameters) {
        Integer result = jdbc.queryForObject(sql, parameters, Integer.class);
        return result == null ? 0 : result;
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
