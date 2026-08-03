package com.wust.dormitory.roomchange;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.security.CurrentUser;
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
public class RoomChangeService {
    private static final Set<String> MODES = Set.of("DISABLED", "FREE", "APPROVAL_REQUIRED");
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "APPROVED");

    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyPolicyService policy;
    private final ResidencyService residencyService;
    private final AuditService auditService;

    public RoomChangeService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyPolicyService policy,
            ResidencyService residencyService,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.policy = policy;
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
        Map<String, Object> current = activeResidency(studentId, false);
        Map<String, Object> student = policy.student(studentId);
        String gender = String.valueOf(student.get("gender"));
        String category = String.valueOf(student.get("student_category"));
        long currentRoomId = number(current.get("room_id"));
        return jdbc.queryForList("""
                SELECT r.id, r.room_number, r.room_type, r.capacity,
                       r.gender_restriction, r.resident_scope,
                       f.floor_number, db.id AS building_id,
                       db.building_code, db.building_name,
                       COUNT(active_ra.id) AS active_residents,
                       GREATEST(r.capacity-COUNT(active_ra.id),0) AS available_count
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building db ON db.id=f.building_id
                LEFT JOIN room_assignment active_ra
                  ON active_ra.room_id=r.id AND active_ra.assignment_status='ACTIVE'
                WHERE r.id<>:currentRoomId
                  AND r.operational_status='ENABLED'
                  AND r.gender_restriction=:gender
                  AND (
                    r.resident_scope='MIXED'
                    OR (:category='DOMESTIC' AND r.resident_scope='DOMESTIC_ONLY')
                    OR (:category='INTERNATIONAL' AND r.resident_scope='INTERNATIONAL_ONLY')
                  )
                GROUP BY r.id, r.room_number, r.room_type, r.capacity,
                         r.gender_restriction, r.resident_scope,
                         f.floor_number, db.id, db.building_code, db.building_name
                HAVING COUNT(active_ra.id)<r.capacity
                ORDER BY db.building_code, f.floor_number, r.room_number
                """, new MapSqlParameterSource()
                .addValue("currentRoomId", currentRoomId)
                .addValue("gender", gender)
                .addValue("category", category));
    }

    public List<Map<String, Object>> listMy(long studentId) {
        return jdbc.queryForList("""
                SELECT request.id, request.request_status, request.policy_mode,
                       request.reason, request.review_reason,
                       request.created_at, request.reviewed_at, request.executed_at,
                       source_room.room_number AS source_room_number,
                       source_building.building_name AS source_building_name,
                       target_room.room_number AS target_room_number,
                       target_building.building_name AS target_building_name,
                       target_bed.bed_code AS target_bed_code
                FROM room_change_request request
                JOIN room source_room ON source_room.id=request.source_room_id
                JOIN dormitory_floor source_floor ON source_floor.id=source_room.floor_id
                JOIN dormitory_building source_building ON source_building.id=source_floor.building_id
                JOIN room target_room ON target_room.id=request.target_room_id
                JOIN dormitory_floor target_floor ON target_floor.id=target_room.floor_id
                JOIN dormitory_building target_building ON target_building.id=target_floor.building_id
                LEFT JOIN bed target_bed ON target_bed.id=request.target_bed_id
                WHERE request.student_id=:studentId
                ORDER BY request.created_at DESC
                """, Map.of("studentId", studentId));
    }

    public List<Map<String, Object>> listAll(String status, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (status != null && !status.isBlank() && !"ALL".equals(status)) {
            where.append(" AND request.request_status=:status ");
            parameters.addValue("status", status);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (student.student_number LIKE :keyword ")
                    .append("OR student.student_name LIKE :keyword ")
                    .append("OR source_room.room_number LIKE :keyword ")
                    .append("OR target_room.room_number LIKE :keyword) ");
            parameters.addValue("keyword", "%" + keyword.trim() + "%");
        }
        return jdbc.queryForList("""
                SELECT request.id, request.request_status, request.policy_mode,
                       request.reason, request.review_reason,
                       request.created_at, request.reviewed_at, request.executed_at,
                       student.id AS student_id, student.student_number, student.student_name,
                       source_room.room_number AS source_room_number,
                       source_building.building_name AS source_building_name,
                       target_room.room_number AS target_room_number,
                       target_building.building_name AS target_building_name,
                       target_bed.bed_code AS target_bed_code,
                       reviewer.display_name AS reviewed_by_name
                FROM room_change_request request
                JOIN student ON student.id=request.student_id
                JOIN room source_room ON source_room.id=request.source_room_id
                JOIN dormitory_floor source_floor ON source_floor.id=source_room.floor_id
                JOIN dormitory_building source_building ON source_building.id=source_floor.building_id
                JOIN room target_room ON target_room.id=request.target_room_id
                JOIN dormitory_floor target_floor ON target_floor.id=target_room.floor_id
                JOIN dormitory_building target_building ON target_building.id=target_floor.building_id
                LEFT JOIN bed target_bed ON target_bed.id=request.target_bed_id
                LEFT JOIN app_user reviewer ON reviewer.id=request.reviewed_by
                """ + where + " ORDER BY request.created_at DESC", parameters);
    }

    @Transactional
    public Map<String, Object> submit(
            long studentId,
            long targetRoomId,
            Long targetBedId,
            String reason,
            CurrentUser studentUser) {
        String mode = currentMode();
        if ("DISABLED".equals(mode)) {
            throw new BusinessException(
                    "ROOM_CHANGE_DISABLED",
                    "学校当前未开放学生换寝",
                    HttpStatus.CONFLICT);
        }
        String normalizedReason = requiredReason(reason);
        Map<String, Object> source = activeResidency(studentId, true);
        requireNoActiveRequest(studentId);
        validateTarget(studentId, targetRoomId, targetBedId, number(source.get("room_id")));

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO room_change_request
                (student_id, source_residency_id, source_room_id,
                 target_room_id, target_bed_id, policy_mode,
                 request_status, reason, created_at, updated_at)
                VALUES
                (:studentId, :sourceResidencyId, :sourceRoomId,
                 :targetRoomId, :targetBedId, :policyMode,
                 'PENDING', :reason, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("sourceResidencyId", source.get("id"))
                .addValue("sourceRoomId", source.get("room_id"))
                .addValue("targetRoomId", targetRoomId)
                .addValue("targetBedId", targetBedId)
                .addValue("policyMode", mode)
                .addValue("reason", normalizedReason),
                keyHolder,
                new String[]{"id"});
        long requestId = keyHolder.getKey().longValue();

        auditService.success(
                studentUser,
                "ROOM_CHANGE_REQUEST_CREATE",
                "ROOM_CHANGE_REQUEST",
                requestId,
                normalizedReason,
                source,
                Map.of("targetRoomId", targetRoomId, "policyMode", mode));

        if ("FREE".equals(mode)) {
            jdbc.update("""
                    UPDATE room_change_request
                    SET request_status='APPROVED', reviewed_by=:reviewedBy,
                        reviewed_at=CURRENT_TIMESTAMP(3), review_reason='系统自由换寝自动批准',
                        updated_at=CURRENT_TIMESTAMP(3)
                    WHERE id=:id
                    """, Map.of("reviewedBy", studentUser.userId(), "id", requestId));
            return executeRoomChange(requestId, studentUser, "学生自由换寝");
        }
        notifyStudent(studentId, "ROOM_CHANGE_SUBMITTED", requestId);
        return request(requestId);
    }

    @Transactional
    public Map<String, Object> approve(long requestId, String reason, CurrentUser admin) {
        Map<String, Object> request = requestForUpdate(requestId);
        requireStatus(request, "PENDING");
        jdbc.update("""
                UPDATE room_change_request
                SET request_status='APPROVED', reviewed_by=:reviewedBy,
                    reviewed_at=CURRENT_TIMESTAMP(3), review_reason=:reason,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("reviewedBy", admin.userId())
                .addValue("reason", requiredReason(reason))
                .addValue("id", requestId));
        return executeRoomChange(requestId, admin, "管理员批准换寝：" + reason.trim());
    }

    @Transactional
    public Map<String, Object> reject(long requestId, String reason, CurrentUser admin) {
        Map<String, Object> before = requestForUpdate(requestId);
        requireStatus(before, "PENDING");
        jdbc.update("""
                UPDATE room_change_request
                SET request_status='REJECTED', reviewed_by=:reviewedBy,
                    reviewed_at=CURRENT_TIMESTAMP(3), review_reason=:reason,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("reviewedBy", admin.userId())
                .addValue("reason", requiredReason(reason))
                .addValue("id", requestId));
        notifyStudent(number(before.get("student_id")), "ROOM_CHANGE_REJECTED", requestId);
        Map<String, Object> after = request(requestId);
        auditService.success(
                admin,
                "ROOM_CHANGE_REJECT",
                "ROOM_CHANGE_REQUEST",
                requestId,
                reason.trim(),
                before,
                after);
        return after;
    }

    @Transactional
    public Map<String, Object> cancel(long requestId, long studentId, String reason, CurrentUser studentUser) {
        Map<String, Object> before = requestForUpdate(requestId);
        if (number(before.get("student_id")) != studentId) {
            throw new BusinessException("ROOM_CHANGE_REQUEST_NOT_FOUND", "换寝申请不存在", HttpStatus.NOT_FOUND);
        }
        requireStatus(before, "PENDING");
        jdbc.update("""
                UPDATE room_change_request
                SET request_status='CANCELLED', review_reason=:reason,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, Map.of("reason", requiredReason(reason), "id", requestId));
        Map<String, Object> after = request(requestId);
        auditService.success(
                studentUser,
                "ROOM_CHANGE_CANCEL",
                "ROOM_CHANGE_REQUEST",
                requestId,
                reason.trim(),
                before,
                after);
        return after;
    }

    @Transactional
    public Map<String, Object> updateSettings(String mode, String reason, CurrentUser admin) {
        if (!MODES.contains(mode)) {
            throw new BusinessException("ROOM_CHANGE_MODE_INVALID", "换寝模式无效");
        }
        String before = currentMode();
        jdbc.update("""
                INSERT INTO system_setting
                (setting_key, setting_value, version, updated_by)
                VALUES ('ROOM_CHANGE_POLICY', :mode, 0, :updatedBy)
                ON DUPLICATE KEY UPDATE
                    setting_value=VALUES(setting_value),
                    version=version+1,
                    updated_by=VALUES(updated_by),
                    updated_at=CURRENT_TIMESTAMP(3)
                """, Map.of("mode", mode, "updatedBy", admin.userId()));
        Map<String, Object> after = policy();
        auditService.success(
                admin,
                "ROOM_CHANGE_POLICY_UPDATE",
                "SYSTEM_SETTING",
                0L,
                requiredReason(reason),
                Map.of("mode", before),
                after);
        return after;
    }

    @Transactional
    public int cancelActiveRoomChanges(long studentId, String reason, CurrentUser operator) {
        List<Long> ids = jdbc.queryForList("""
                SELECT id FROM room_change_request
                WHERE student_id=:studentId
                  AND request_status IN ('PENDING','APPROVED')
                FOR UPDATE
                """, Map.of("studentId", studentId), Long.class);
        if (ids.isEmpty()) {
            return 0;
        }
        int changed = jdbc.update("""
                UPDATE room_change_request
                SET request_status='CANCELLED', review_reason=:reason,
                    reviewed_by=:reviewedBy, reviewed_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE student_id=:studentId
                  AND request_status IN ('PENDING','APPROVED')
                """, new MapSqlParameterSource()
                .addValue("reason", requiredReason(reason))
                .addValue("reviewedBy", operator.userId())
                .addValue("studentId", studentId));
        auditService.success(
                operator,
                "ROOM_CHANGE_CANCEL_FOR_RESET",
                "STUDENT",
                studentId,
                reason,
                Map.of("requestIds", ids),
                Map.of("cancelledCount", changed));
        return changed;
    }

    private Map<String, Object> executeRoomChange(long requestId, CurrentUser operator, String reason) {
        Map<String, Object> change = requestForUpdate(requestId);
        if (!"APPROVED".equals(String.valueOf(change.get("request_status")))) {
            throw new BusinessException("ROOM_CHANGE_NOT_APPROVED", "换寝申请尚未批准", HttpStatus.CONFLICT);
        }
        long studentId = number(change.get("student_id"));
        long sourceResidencyId = number(change.get("source_residency_id"));
        long sourceRoomId = number(change.get("source_room_id"));
        long targetRoomId = number(change.get("target_room_id"));
        Long targetBedId = nullableNumber(change.get("target_bed_id"));

        Map<String, Object> current = activeResidency(studentId, true);
        if (number(current.get("id")) != sourceResidencyId || number(current.get("room_id")) != sourceRoomId) {
            failRequest(requestId, "原住宿记录已经变化");
            throw new BusinessException(
                    "ROOM_CHANGE_SOURCE_CHANGED",
                    "原住宿记录已经变化，请重新提交换寝申请",
                    HttpStatus.CONFLICT);
        }
        validateTarget(studentId, targetRoomId, targetBedId, sourceRoomId);
        residencyService.end(sourceResidencyId, reason, operator);
        Map<String, Object> newResidency = residencyService.assign(
                studentId,
                targetRoomId,
                targetBedId,
                null,
                null,
                "DIRECT",
                "MANUAL_ADJUSTMENT",
                reason,
                operator);
        long newResidencyId = number(newResidency.get("id"));
        jdbc.update("""
                UPDATE room_change_request
                SET request_status='EXECUTED', executed_residency_id=:residencyId,
                    executed_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, Map.of("residencyId", newResidencyId, "id", requestId));
        notifyStudent(studentId, "ROOM_CHANGE_EXECUTED", requestId);
        Map<String, Object> after = request(requestId);
        auditService.success(
                operator,
                "ROOM_CHANGE_EXECUTE",
                "ROOM_CHANGE_REQUEST",
                requestId,
                reason,
                change,
                after);
        return after;
    }

    private void validateTarget(long studentId, long roomId, Long bedId, long sourceRoomId) {
        if (roomId == sourceRoomId) {
            throw new BusinessException("ROOM_CHANGE_SAME_ROOM", "目标寝室不能与当前寝室相同");
        }
        Map<String, Object> student = policy.student(studentId);
        Map<String, Object> room = policy.room(roomId, true);
        Map<String, Object> batch = Map.of("separate_student_categories", 0, "selection_mode", "DIRECT");
        policy.requireStudentEligibleForRoom(student, batch, room);
        policy.requireRoomCapacity(roomId, 1);
        if (bedId != null) {
            policy.requireAvailableBed(roomId, bedId);
        }
    }

    private Map<String, Object> activeResidency(long studentId, boolean lock) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM room_assignment
                WHERE student_id=:studentId AND assignment_status='ACTIVE'
                """ + (lock ? " FOR UPDATE" : ""), Map.of("studentId", studentId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "ROOM_CHANGE_RESIDENCY_REQUIRED",
                    "只有当前已入住学生可以申请换寝",
                    HttpStatus.CONFLICT);
        }
        return rows.getFirst();
    }

    private void requireNoActiveRequest(long studentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM room_change_request
                WHERE student_id=:studentId
                  AND request_status IN ('PENDING','APPROVED')
                """, Map.of("studentId", studentId), Integer.class);
        if (count != null && count > 0) {
            throw new BusinessException(
                    "ROOM_CHANGE_REQUEST_ACTIVE",
                    "你已经有一条待处理换寝申请",
                    HttpStatus.CONFLICT);
        }
    }

    private String currentMode() {
        List<String> rows = jdbc.query("""
                SELECT setting_value FROM system_setting
                WHERE setting_key='ROOM_CHANGE_POLICY'
                """, Map.of(), (rs, rowNum) -> rs.getString(1));
        String mode = rows.isEmpty() ? "DISABLED" : rows.getFirst();
        return MODES.contains(mode) ? mode : "DISABLED";
    }

    private Map<String, Object> request(long requestId) {
        return jdbc.queryForMap("SELECT * FROM room_change_request WHERE id=:id", Map.of("id", requestId));
    }

    private Map<String, Object> requestForUpdate(long requestId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM room_change_request WHERE id=:id FOR UPDATE",
                Map.of("id", requestId));
        if (rows.isEmpty()) {
            throw new BusinessException("ROOM_CHANGE_REQUEST_NOT_FOUND", "换寝申请不存在", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void requireStatus(Map<String, Object> request, String status) {
        if (!status.equals(String.valueOf(request.get("request_status")))) {
            throw new BusinessException(
                    "ROOM_CHANGE_STATUS_INVALID",
                    "换寝申请当前状态不允许执行此操作",
                    HttpStatus.CONFLICT);
        }
    }

    private void failRequest(long requestId, String reason) {
        jdbc.update("""
                UPDATE room_change_request
                SET request_status='FAILED', review_reason=:reason,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, Map.of("reason", reason, "id", requestId));
    }

    private void notifyStudent(long studentId, String type, long requestId) {
        jdbc.update("""
                INSERT INTO student_notification
                (student_id, notification_type, title_key, message_key, parameters_json, read_at)
                VALUES
                (:studentId, :type,
                 CONCAT('notification.', LOWER(:type), '.title'),
                 CONCAT('notification.', LOWER(:type), '.message'),
                 JSON_OBJECT('requestId', :requestId), NULL)
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("type", type)
                .addValue("requestId", requestId));
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new BusinessException("ROOM_CHANGE_REASON_REQUIRED", "请填写1至500个字符的原因");
        }
        return reason.trim();
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableNumber(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
