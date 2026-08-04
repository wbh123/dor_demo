package com.wust.dormitory.roomexchange;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoomExchangeService {
    private static final String SETTING_KEY = "ROOM_EXCHANGE_POLICY";
    private static final Set<String> MODES = Set.of(
            "DISABLED", "MUTUAL_CONFIRMATION", "APPROVAL_REQUIRED");
    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "WAITING_TARGET", "PENDING_ADMIN", "APPROVED");

    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyPolicyService residencyPolicy;
    private final ResidencyService residencyService;
    private final AuditService auditService;

    public RoomExchangeService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyPolicyService residencyPolicy,
            ResidencyService residencyService,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.residencyPolicy = residencyPolicy;
        this.residencyService = residencyService;
        this.auditService = auditService;
    }

    public Map<String, Object> policy() {
        String mode = currentMode();
        return Map.of(
                "mode", mode,
                "enabled", !"DISABLED".equals(mode),
                "requiresApproval", "APPROVAL_REQUIRED".equals(mode));
    }

    public List<Map<String, Object>> candidates(long studentId) {
        if ("DISABLED".equals(currentMode())) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_DISABLED",
                    "学校当前未开放学生寝室交换",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> source = activeResidency(studentId, false);
        Map<String, Object> sourceStudent = residencyPolicy.student(studentId);
        Map<String, Object> sourceRoom = residencyPolicy.room(number(source.get("room_id")), false);
        return jdbc.queryForList("""
                SELECT target.id AS target_student_id,
                       target.student_number, target.student_name,
                       target.gender, target.student_category,
                       assignment.id AS target_residency_id,
                       assignment.room_id, assignment.bed_id,
                       building.building_name, floor.floor_number,
                       room.room_number, room.room_type,
                       bed.bed_code, bed.bed_type
                FROM student target
                JOIN room_assignment assignment
                  ON assignment.student_id=target.id
                 AND assignment.assignment_status='ACTIVE'
                JOIN room ON room.id=assignment.room_id
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                JOIN dormitory_building building ON building.id=floor.building_id
                LEFT JOIN bed ON bed.id=assignment.bed_id
                LEFT JOIN room_exchange_participant_lock participant_lock
                  ON participant_lock.student_id=target.id
                WHERE target.id<>:studentId
                  AND target.gender=:gender
                  AND participant_lock.student_id IS NULL
                  AND room.operational_status='ENABLED'
                ORDER BY building.building_code, floor.floor_number,
                         room.room_number, target.student_number
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("gender", sourceStudent.get("gender")))
                .stream()
                .filter(candidate -> compatible(
                        sourceStudent,
                        sourceRoom,
                        residencyPolicy.student(number(candidate.get("target_student_id"))),
                        residencyPolicy.room(number(candidate.get("room_id")), false)))
                .toList();
    }

    public List<Map<String, Object>> listMy(long studentId) {
        return list("ALL", "", studentId);
    }

    public List<Map<String, Object>> listAdmin(String status, String keyword) {
        return list(status, keyword, null);
    }

    @Transactional
    public Map<String, Object> submit(
            long initiatorStudentId,
            long targetStudentId,
            String reason,
            CurrentUser initiator) {
        String mode = currentMode();
        if ("DISABLED".equals(mode)) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_DISABLED",
                    "学校当前未开放学生寝室交换",
                    HttpStatus.CONFLICT);
        }
        if (initiatorStudentId == targetStudentId) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_SAME_STUDENT",
                    "不能向本人发起寝室交换");
        }
        List<Map<String, Object>> residencies = lockActiveResidencies(
                initiatorStudentId,
                targetStudentId);
        Map<String, Object> initiatorResidency = residencyOf(residencies, initiatorStudentId);
        Map<String, Object> targetResidency = residencyOf(residencies, targetStudentId);
        requireCompatible(initiatorStudentId, initiatorResidency, targetStudentId, targetResidency);

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO room_exchange_request
                (initiator_student_id, target_student_id,
                 initiator_residency_id, target_residency_id,
                 policy_mode, request_status, reason,
                 created_at, updated_at)
                VALUES
                (:initiatorStudentId, :targetStudentId,
                 :initiatorResidencyId, :targetResidencyId,
                 :policyMode, 'WAITING_TARGET', :reason,
                 CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("initiatorStudentId", initiatorStudentId)
                .addValue("targetStudentId", targetStudentId)
                .addValue("initiatorResidencyId", initiatorResidency.get("id"))
                .addValue("targetResidencyId", targetResidency.get("id"))
                .addValue("policyMode", mode)
                .addValue("reason", requiredReason(reason)),
                keyHolder,
                new String[]{"id"});
        long exchangeId = keyHolder.getKey().longValue();
        try {
            lockParticipant(exchangeId, initiatorStudentId, "INITIATOR");
            lockParticipant(exchangeId, targetStudentId, "TARGET");
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_PARTICIPANT_BUSY",
                    "本人或对方已有进行中的寝室交换",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> after = request(exchangeId);
        auditService.success(
                initiator,
                "ROOM_EXCHANGE_CREATE",
                "ROOM_EXCHANGE_REQUEST",
                exchangeId,
                reason.trim(),
                null,
                after);
        return after;
    }

    @Transactional
    public Map<String, Object> respond(
            long exchangeId,
            long targetStudentId,
            boolean accepted,
            String reason,
            CurrentUser targetUser) {
        Map<String, Object> before = requestForUpdate(exchangeId);
        if (number(before.get("target_student_id")) != targetStudentId) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_NOT_FOUND",
                    "寝室交换邀请不存在",
                    HttpStatus.NOT_FOUND);
        }
        requireStatus(before, "WAITING_TARGET");
        if (!accepted) {
            updateTerminal(
                    exchangeId,
                    "REJECTED",
                    "target_response_reason",
                    requiredReason(reason),
                    "responded_at");
            releaseParticipants(exchangeId);
            Map<String, Object> after = request(exchangeId);
            auditService.success(targetUser, "ROOM_EXCHANGE_REJECT_BY_TARGET",
                    "ROOM_EXCHANGE_REQUEST", exchangeId, reason.trim(), before, after);
            return after;
        }

        String mode = String.valueOf(before.get("policy_mode"));
        String nextStatus = "APPROVAL_REQUIRED".equals(mode)
                ? "PENDING_ADMIN"
                : "APPROVED";
        jdbc.update("""
                UPDATE room_exchange_request
                SET request_status=:status,
                    target_response_reason=:reason,
                    responded_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("status", nextStatus)
                .addValue("reason", requiredReason(reason))
                .addValue("id", exchangeId));
        if ("MUTUAL_CONFIRMATION".equals(mode)) {
            return executeExchange(exchangeId, targetUser, "双方确认后自动交换寝室床位");
        }
        Map<String, Object> after = request(exchangeId);
        auditService.success(targetUser, "ROOM_EXCHANGE_ACCEPT_BY_TARGET",
                "ROOM_EXCHANGE_REQUEST", exchangeId, reason.trim(), before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> approve(
            long exchangeId,
            String reason,
            CurrentUser admin) {
        Map<String, Object> before = requestForUpdate(exchangeId);
        requireStatus(before, "PENDING_ADMIN");
        jdbc.update("""
                UPDATE room_exchange_request
                SET request_status='APPROVED', reviewed_by=:reviewedBy,
                    review_reason=:reason, reviewed_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("reviewedBy", admin.userId())
                .addValue("reason", requiredReason(reason))
                .addValue("id", exchangeId));
        return executeExchange(exchangeId, admin, "管理员批准寝室交换：" + reason.trim());
    }

    @Transactional
    public Map<String, Object> reject(
            long exchangeId,
            String reason,
            CurrentUser admin) {
        Map<String, Object> before = requestForUpdate(exchangeId);
        requireStatus(before, "PENDING_ADMIN");
        jdbc.update("""
                UPDATE room_exchange_request
                SET request_status='REJECTED', reviewed_by=:reviewedBy,
                    review_reason=:reason, reviewed_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("reviewedBy", admin.userId())
                .addValue("reason", requiredReason(reason))
                .addValue("id", exchangeId));
        releaseParticipants(exchangeId);
        Map<String, Object> after = request(exchangeId);
        auditService.success(admin, "ROOM_EXCHANGE_REJECT",
                "ROOM_EXCHANGE_REQUEST", exchangeId, reason.trim(), before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> cancel(
            long exchangeId,
            long initiatorStudentId,
            String reason,
            CurrentUser initiator) {
        Map<String, Object> before = requestForUpdate(exchangeId);
        if (number(before.get("initiator_student_id")) != initiatorStudentId) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_NOT_FOUND",
                    "寝室交换申请不存在",
                    HttpStatus.NOT_FOUND);
        }
        String status = String.valueOf(before.get("request_status"));
        if (!Set.of("WAITING_TARGET", "PENDING_ADMIN").contains(status)) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_STATE_INVALID",
                    "当前状态不能取消",
                    HttpStatus.CONFLICT);
        }
        updateTerminal(exchangeId, "CANCELLED", "review_reason",
                requiredReason(reason), null);
        releaseParticipants(exchangeId);
        Map<String, Object> after = request(exchangeId);
        auditService.success(initiator, "ROOM_EXCHANGE_CANCEL",
                "ROOM_EXCHANGE_REQUEST", exchangeId, reason.trim(), before, after);
        return after;
    }

    @Transactional
    public Map<String, Object> updateSettings(
            String mode,
            String reason,
            CurrentUser admin) {
        if (!MODES.contains(mode)) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_MODE_INVALID",
                    "寝室交换模式无效");
        }
        String before = currentMode();
        jdbc.update("""
                INSERT INTO system_setting
                (setting_key, setting_value, version, updated_by)
                VALUES (:settingKey, :mode, 0, :updatedBy)
                ON DUPLICATE KEY UPDATE
                    setting_value=VALUES(setting_value),
                    version=version+1,
                    updated_by=VALUES(updated_by),
                    updated_at=CURRENT_TIMESTAMP(3)
                """, new MapSqlParameterSource()
                .addValue("settingKey", SETTING_KEY)
                .addValue("mode", mode)
                .addValue("updatedBy", admin.userId()));
        Map<String, Object> after = policy();
        auditService.success(admin, "ROOM_EXCHANGE_POLICY_UPDATE",
                "SYSTEM_SETTING", 0L, requiredReason(reason),
                Map.of("mode", before), after);
        return after;
    }

    private Map<String, Object> executeExchange(
            long exchangeId,
            CurrentUser operator,
            String reason) {
        Map<String, Object> before = requestForUpdate(exchangeId);
        requireStatus(before, "APPROVED");
        long initiatorStudentId = number(before.get("initiator_student_id"));
        long targetStudentId = number(before.get("target_student_id"));
        List<Map<String, Object>> current = lockActiveResidencies(
                initiatorStudentId,
                targetStudentId);
        Map<String, Object> initiatorResidency = residencyOf(current, initiatorStudentId);
        Map<String, Object> targetResidency = residencyOf(current, targetStudentId);
        if (number(initiatorResidency.get("id")) != number(before.get("initiator_residency_id"))
                || number(targetResidency.get("id")) != number(before.get("target_residency_id"))) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_RESIDENCY_CHANGED",
                    "双方住宿记录已经变化，请重新发起交换",
                    HttpStatus.CONFLICT);
        }
        requireCompatible(initiatorStudentId, initiatorResidency,
                targetStudentId, targetResidency);

        residencyService.end(number(initiatorResidency.get("id")), reason, operator);
        residencyService.end(number(targetResidency.get("id")), reason, operator);
        Map<String, Object> initiatorNew = residencyService.assign(
                initiatorStudentId,
                number(targetResidency.get("room_id")),
                nullableNumber(targetResidency.get("bed_id")),
                null, null, "DIRECT", "ROOM_EXCHANGE", reason, operator);
        Map<String, Object> targetNew = residencyService.assign(
                targetStudentId,
                number(initiatorResidency.get("room_id")),
                nullableNumber(initiatorResidency.get("bed_id")),
                null, null, "DIRECT", "ROOM_EXCHANGE", reason, operator);
        jdbc.update("""
                UPDATE room_exchange_request
                SET request_status='EXECUTED',
                    initiator_executed_residency_id=:initiatorResidencyId,
                    target_executed_residency_id=:targetResidencyId,
                    executed_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("initiatorResidencyId", initiatorNew.get("id"))
                .addValue("targetResidencyId", targetNew.get("id"))
                .addValue("id", exchangeId));
        releaseParticipants(exchangeId);
        Map<String, Object> after = request(exchangeId);
        auditService.success(operator, "ROOM_EXCHANGE_EXECUTE",
                "ROOM_EXCHANGE_REQUEST", exchangeId, reason, before, after);
        return after;
    }

    private List<Map<String, Object>> list(
            String status,
            String keyword,
            Long studentId) {
        return jdbc.queryForList("""
                SELECT exchange_row.*,
                       initiator.student_number AS initiator_student_number,
                       initiator.student_name AS initiator_student_name,
                       target.student_number AS target_student_number,
                       target.student_name AS target_student_name,
                       initiator_room.room_number AS initiator_room_number,
                       initiator_building.building_name AS initiator_building_name,
                       initiator_bed.bed_code AS initiator_bed_code,
                       target_room.room_number AS target_room_number,
                       target_building.building_name AS target_building_name,
                       target_bed.bed_code AS target_bed_code
                FROM room_exchange_request exchange_row
                JOIN student initiator ON initiator.id=exchange_row.initiator_student_id
                JOIN student target ON target.id=exchange_row.target_student_id
                JOIN room_assignment initiator_residency
                  ON initiator_residency.id=exchange_row.initiator_residency_id
                JOIN room initiator_room ON initiator_room.id=initiator_residency.room_id
                JOIN dormitory_floor initiator_floor ON initiator_floor.id=initiator_room.floor_id
                JOIN dormitory_building initiator_building
                  ON initiator_building.id=initiator_floor.building_id
                LEFT JOIN bed initiator_bed ON initiator_bed.id=initiator_residency.bed_id
                JOIN room_assignment target_residency
                  ON target_residency.id=exchange_row.target_residency_id
                JOIN room target_room ON target_room.id=target_residency.room_id
                JOIN dormitory_floor target_floor ON target_floor.id=target_room.floor_id
                JOIN dormitory_building target_building
                  ON target_building.id=target_floor.building_id
                LEFT JOIN bed target_bed ON target_bed.id=target_residency.bed_id
                WHERE (:studentId IS NULL
                       OR exchange_row.initiator_student_id=:studentId
                       OR exchange_row.target_student_id=:studentId)
                  AND (:status='ALL' OR exchange_row.request_status=:status)
                  AND (:keyword='' OR initiator.student_number LIKE CONCAT('%',:keyword,'%')
                       OR initiator.student_name LIKE CONCAT('%',:keyword,'%')
                       OR target.student_number LIKE CONCAT('%',:keyword,'%')
                       OR target.student_name LIKE CONCAT('%',:keyword,'%'))
                ORDER BY exchange_row.created_at DESC
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("status", status == null ? "ALL" : status)
                .addValue("keyword", keyword == null ? "" : keyword.trim()));
    }

    private Map<String, Object> request(long exchangeId) {
        List<Map<String, Object>> rows = list("ALL", "", null).stream()
                .filter(item -> number(item.get("id")) == exchangeId)
                .toList();
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_NOT_FOUND",
                    "寝室交换申请不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private Map<String, Object> requestForUpdate(long exchangeId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM room_exchange_request
                WHERE id=:id FOR UPDATE
                """, Map.of("id", exchangeId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_NOT_FOUND",
                    "寝室交换申请不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private List<Map<String, Object>> lockActiveResidencies(
            long firstStudentId,
            long secondStudentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM room_assignment
                WHERE student_id IN (:firstStudentId,:secondStudentId)
                  AND assignment_status='ACTIVE'
                ORDER BY student_id
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("firstStudentId", firstStudentId)
                .addValue("secondStudentId", secondStudentId));
        if (rows.size() != 2) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_RESIDENCY_REQUIRED",
                    "双方都必须只有一条有效在住记录",
                    HttpStatus.CONFLICT);
        }
        return rows;
    }

    private Map<String, Object> activeResidency(
            long studentId,
            boolean forUpdate) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM room_assignment
                WHERE student_id=:studentId AND assignment_status='ACTIVE'
                """ + (forUpdate ? " FOR UPDATE" : ""),
                Map.of("studentId", studentId));
        if (rows.size() != 1) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_RESIDENCY_REQUIRED",
                    "只有当前已入住且住宿记录正常的学生可以交换",
                    HttpStatus.CONFLICT);
        }
        return rows.getFirst();
    }

    private Map<String, Object> residencyOf(
            List<Map<String, Object>> rows,
            long studentId) {
        return rows.stream()
                .filter(row -> number(row.get("student_id")) == studentId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "ROOM_EXCHANGE_RESIDENCY_REQUIRED",
                        "没有找到有效住宿记录",
                        HttpStatus.CONFLICT));
    }

    private void requireCompatible(
            long initiatorStudentId,
            Map<String, Object> initiatorResidency,
            long targetStudentId,
            Map<String, Object> targetResidency) {
        Map<String, Object> initiatorStudent = residencyPolicy.student(initiatorStudentId);
        Map<String, Object> targetStudent = residencyPolicy.student(targetStudentId);
        Map<String, Object> initiatorRoom = residencyPolicy.room(
                number(initiatorResidency.get("room_id")), true);
        Map<String, Object> targetRoom = residencyPolicy.room(
                number(targetResidency.get("room_id")), true);
        if (!compatible(initiatorStudent, initiatorRoom, targetStudent, targetRoom)) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_POLICY_MISMATCH",
                    "双方不符合对方寝室的性别或学生类别要求",
                    HttpStatus.CONFLICT);
        }
    }

    private boolean compatible(
            Map<String, Object> initiatorStudent,
            Map<String, Object> initiatorRoom,
            Map<String, Object> targetStudent,
            Map<String, Object> targetRoom) {
        return "ENABLED".equals(String.valueOf(initiatorRoom.get("operational_status")))
                && "ENABLED".equals(String.valueOf(targetRoom.get("operational_status")))
                && String.valueOf(initiatorStudent.get("gender"))
                    .equals(String.valueOf(targetRoom.get("gender_restriction")))
                && String.valueOf(targetStudent.get("gender"))
                    .equals(String.valueOf(initiatorRoom.get("gender_restriction")))
                && residencyPolicy.roomAllowsCategory(
                    String.valueOf(targetRoom.get("resident_scope")),
                    String.valueOf(initiatorStudent.get("student_category")), false)
                && residencyPolicy.roomAllowsCategory(
                    String.valueOf(initiatorRoom.get("resident_scope")),
                    String.valueOf(targetStudent.get("student_category")), false);
    }

    private void lockParticipant(
            long exchangeId,
            long studentId,
            String role) {
        jdbc.update("""
                INSERT INTO room_exchange_participant_lock
                (student_id, exchange_id, participant_role, created_at)
                VALUES (:studentId,:exchangeId,:role,CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("exchangeId", exchangeId)
                .addValue("role", role));
    }

    private void releaseParticipants(long exchangeId) {
        jdbc.update("""
                DELETE FROM room_exchange_participant_lock
                WHERE exchange_id=:exchangeId
                """, Map.of("exchangeId", exchangeId));
    }

    private void updateTerminal(
            long exchangeId,
            String status,
            String reasonColumn,
            String reason,
            String timeColumn) {
        String timeUpdate = timeColumn == null
                ? ""
                : ", " + timeColumn + "=CURRENT_TIMESTAMP(3)";
        jdbc.update("UPDATE room_exchange_request SET request_status=:status, "
                        + reasonColumn + "=:reason" + timeUpdate
                        + ", updated_at=CURRENT_TIMESTAMP(3) WHERE id=:id",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("reason", reason)
                        .addValue("id", exchangeId));
    }

    private String currentMode() {
        List<String> rows = jdbc.query("""
                SELECT setting_value FROM system_setting
                WHERE setting_key=:settingKey
                """, Map.of("settingKey", SETTING_KEY),
                (resultSet, rowNumber) -> resultSet.getString(1));
        return rows.isEmpty() ? "DISABLED" : rows.getFirst();
    }

    private void requireStatus(
            Map<String, Object> request,
            String expected) {
        if (!expected.equals(String.valueOf(request.get("request_status")))) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_STATE_INVALID",
                    "寝室交换当前状态不允许该操作",
                    HttpStatus.CONFLICT);
        }
    }

    private String requiredReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new BusinessException(
                    "ROOM_EXCHANGE_REASON_INVALID",
                    "原因必须填写且不能超过500个字符");
        }
        return normalized;
    }

    private int number(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
