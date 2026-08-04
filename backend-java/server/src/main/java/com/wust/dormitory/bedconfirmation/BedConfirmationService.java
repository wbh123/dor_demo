package com.wust.dormitory.bedconfirmation;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BedConfirmationService {
    private static final String PENDING = "PENDING";
    private static final Set<String> STUDENT_SELECTION_METHODS = Set.of(
            "ROOM_SELECT", "TEAM_ROOM_SELECT", "BED_SELECT", "TEAM_BED_SELECT");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public BedConfirmationService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public Map<String, Object> my(long studentId) {
        Map<String, Object> residency = currentResidency(studentId, false);
        if (residency.isEmpty()) return Map.of("resident", false, "eligible", false);
        boolean eligible = STUDENT_SELECTION_METHODS.contains(String.valueOf(residency.get("assignment_method")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resident", true);
        result.put("eligible", eligible);
        result.put("residency", residency);
        result.put("beds", roomBeds(number(residency, "room_id")));
        result.put("request", pendingForResidency(number(residency, "residency_id")));
        result.put("message", eligible
                ? "请选择你当前实际使用的床位，提交后由管理员按寝室核查"
                : "当前住宿结果不是学生选寝产生，不能自主申报实际床位");
        return result;
    }

    @Transactional
    public Map<String, Object> submit(
            long studentId,
            long bedId,
            String reason,
            CurrentUser user) {
        Map<String, Object> residency = currentResidency(studentId, true);
        if (residency.isEmpty()) {
            throw new BusinessException("RESIDENCY_NOT_FOUND", "当前没有有效住宿记录", HttpStatus.NOT_FOUND);
        }
        if (!STUDENT_SELECTION_METHODS.contains(String.valueOf(residency.get("assignment_method")))) {
            throw new BusinessException(
                    "BED_CONFIRMATION_NOT_AVAILABLE",
                    "只有通过学生选寝产生的住宿结果可以申报实际床位",
                    HttpStatus.FORBIDDEN);
        }
        String normalizedReason = requireReason(reason);
        long roomId = number(residency, "room_id");
        Map<String, Object> bed = lockBed(roomId, bedId);
        if (!"ENABLED".equals(String.valueOf(bed.get("operational_status")))) {
            throw new BusinessException("BED_NOT_AVAILABLE", "该床位当前不可使用", HttpStatus.CONFLICT);
        }
        if (!pendingForResidency(number(residency, "residency_id")).isEmpty()) {
            throw new BusinessException(
                    "BED_CONFIRMATION_PENDING_EXISTS",
                    "当前已有一条待管理员核查的床位申报",
                    HttpStatus.CONFLICT);
        }
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO bed_confirmation_request
                (residency_id, student_id, room_id, declared_bed_id,
                 request_status, reason, submitted_at)
                VALUES (:residencyId,:studentId,:roomId,:bedId,'PENDING',:reason,CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("residencyId", residency.get("residency_id"))
                .addValue("studentId", studentId)
                .addValue("roomId", roomId)
                .addValue("bedId", bedId)
                .addValue("reason", normalizedReason),
                keyHolder,
                new String[]{"id"});
        long requestId = keyHolder.getKey().longValue();
        Map<String, Object> after = request(requestId);
        auditService.success(user, "BED_CONFIRMATION_SUBMIT", "BED_CONFIRMATION_REQUEST",
                requestId, normalizedReason, null, after);
        return after;
    }

    @Transactional
    public Map<String, Object> cancel(
            long requestId,
            long studentId,
            String reason,
            CurrentUser user) {
        Map<String, Object> before = lockRequest(requestId);
        if (number(before, "student_id") != studentId || !PENDING.equals(before.get("request_status"))) {
            throw new BusinessException("BED_CONFIRMATION_NOT_FOUND", "待核查申请不存在", HttpStatus.NOT_FOUND);
        }
        String normalizedReason = requireReason(reason);
        jdbc.update("""
                UPDATE bed_confirmation_request
                SET request_status='CANCELLED', review_reason=:reason,
                    reviewed_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:requestId
                """, Map.of("requestId", requestId, "reason", normalizedReason));
        Map<String, Object> after = request(requestId);
        auditService.success(user, "BED_CONFIRMATION_CANCEL", "BED_CONFIRMATION_REQUEST",
                requestId, normalizedReason, before, after);
        return after;
    }

    public List<Map<String, Object>> rooms(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        return jdbc.queryForList("""
                SELECT r.id AS room_id, db.building_name, f.floor_number,
                       r.room_number, r.capacity,
                       COUNT(DISTINCT ra.id) AS resident_count,
                       COUNT(DISTINCT CASE WHEN request.request_status='PENDING' THEN request.id END) AS pending_count,
                       COUNT(DISTINCT CASE WHEN request.request_status='PENDING'
                           AND (occupied.id IS NOT NULL OR duplicate_request.id IS NOT NULL)
                           THEN request.id END) AS conflict_count
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building db ON db.id=f.building_id
                LEFT JOIN room_assignment ra ON ra.room_id=r.id AND ra.assignment_status='ACTIVE'
                LEFT JOIN bed_confirmation_request request ON request.residency_id=ra.id AND request.request_status='PENDING'
                LEFT JOIN room_assignment occupied ON occupied.bed_id=request.declared_bed_id
                    AND occupied.assignment_status='ACTIVE' AND occupied.id<>request.residency_id
                LEFT JOIN bed_confirmation_request duplicate_request
                    ON duplicate_request.room_id=request.room_id
                    AND duplicate_request.declared_bed_id=request.declared_bed_id
                    AND duplicate_request.request_status='PENDING'
                    AND duplicate_request.id<request.id
                WHERE (:keyword='' OR db.building_name LIKE CONCAT('%',:keyword,'%')
                    OR r.room_number LIKE CONCAT('%',:keyword,'%'))
                GROUP BY r.id, db.building_name, f.floor_number, r.room_number, r.capacity
                HAVING pending_count>0
                ORDER BY db.building_name, f.floor_number, r.room_number
                """, Map.of("keyword", normalized));
    }

    public Map<String, Object> room(long roomId) {
        Map<String, Object> room = roomInfo(roomId, false);
        Map<String, Object> result = new LinkedHashMap<>(room);
        result.put("beds", roomBeds(roomId));
        result.put("students", jdbc.queryForList("""
                SELECT ra.id AS residency_id, ra.student_id, student.student_number,
                       student.student_name, ra.bed_id AS current_bed_id,
                       current_bed.bed_code AS current_bed_code,
                       request.id AS request_id, request.declared_bed_id,
                       declared_bed.bed_code AS declared_bed_code,
                       request.reason, request.submitted_at,
                       CASE
                         WHEN request.id IS NULL THEN 'NONE'
                         WHEN occupied.id IS NOT NULL THEN 'OCCUPIED'
                         WHEN duplicate_request.id IS NOT NULL THEN 'DUPLICATE'
                         ELSE 'READY'
                       END AS review_state
                FROM room_assignment ra
                JOIN student ON student.id=ra.student_id
                LEFT JOIN bed current_bed ON current_bed.id=ra.bed_id
                LEFT JOIN bed_confirmation_request request
                    ON request.residency_id=ra.id AND request.request_status='PENDING'
                LEFT JOIN bed declared_bed ON declared_bed.id=request.declared_bed_id
                LEFT JOIN room_assignment occupied ON occupied.bed_id=request.declared_bed_id
                    AND occupied.assignment_status='ACTIVE' AND occupied.id<>ra.id
                LEFT JOIN bed_confirmation_request duplicate_request
                    ON duplicate_request.room_id=request.room_id
                    AND duplicate_request.declared_bed_id=request.declared_bed_id
                    AND duplicate_request.request_status='PENDING'
                    AND duplicate_request.id<request.id
                WHERE ra.room_id=:roomId AND ra.assignment_status='ACTIVE'
                ORDER BY student.student_number
                """, Map.of("roomId", roomId)));
        return result;
    }

    @Transactional
    public Map<String, Object> approveRoom(
            long roomId,
            String reason,
            CurrentUser admin) {
        String normalizedReason = requireReason(reason);
        roomInfo(roomId, true);
        List<Map<String, Object>> pending = jdbc.queryForList("""
                SELECT request.id, request.residency_id, request.student_id,
                       request.declared_bed_id, bed.bed_code, bed.operational_status
                FROM bed_confirmation_request request
                JOIN bed ON bed.id=request.declared_bed_id
                WHERE request.room_id=:roomId AND request.request_status='PENDING'
                ORDER BY request.submitted_at, request.id
                FOR UPDATE
                """, Map.of("roomId", roomId));
        if (pending.isEmpty()) {
            throw new BusinessException("BED_CONFIRMATION_PENDING_EMPTY", "该寝室没有待核查申请", HttpStatus.NOT_FOUND);
        }
        jdbc.queryForList("SELECT id FROM room_assignment WHERE room_id=:roomId AND assignment_status='ACTIVE' FOR UPDATE",
                Map.of("roomId", roomId));
        jdbc.queryForList("SELECT id FROM bed WHERE room_id=:roomId FOR UPDATE", Map.of("roomId", roomId));

        Map<Long, Integer> declarationCounts = new HashMap<>();
        for (Map<String, Object> item : pending) {
            declarationCounts.merge(number(item, "declared_bed_id"), 1, Integer::sum);
        }
        Set<Long> occupiedByOther = new HashSet<>(jdbc.query("""
                SELECT bed_id FROM room_assignment
                WHERE room_id=:roomId AND assignment_status='ACTIVE' AND bed_id IS NOT NULL
                """, Map.of("roomId", roomId), (rs, rowNum) -> rs.getLong(1)));
        List<Map<String, Object>> approved = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        for (Map<String, Object> item : pending) {
            long requestId = number(item, "id");
            long residencyId = number(item, "residency_id");
            long studentId = number(item, "student_id");
            long bedId = number(item, "declared_bed_id");
            boolean occupiedBySelf = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM room_assignment
                    WHERE id=:residencyId AND bed_id=:bedId AND assignment_status='ACTIVE'
                    """, Map.of("residencyId", residencyId, "bedId", bedId), Integer.class) > 0;
            String conflict = null;
            if (!"ENABLED".equals(String.valueOf(item.get("operational_status")))) conflict = "床位不可用";
            else if (declarationCounts.getOrDefault(bedId, 0) > 1) conflict = "多人申报同一床位";
            else if (occupiedByOther.contains(bedId) && !occupiedBySelf) conflict = "床位已被其他在住学生占用";
            if (conflict != null) {
                conflicts.add(Map.of("requestId", requestId, "bedId", bedId, "message", conflict));
                continue;
            }
            jdbc.update("""
                    UPDATE room_assignment
                    SET bed_id=:bedId, source_selection_mode='BED',
                        bed_confirmed_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3)
                    WHERE id=:residencyId AND assignment_status='ACTIVE'
                    """, Map.of("bedId", bedId, "residencyId", residencyId));
            jdbc.update("""
                    UPDATE bed_confirmation_request
                    SET request_status='APPROVED', reviewed_by=:reviewedBy,
                        review_reason=:reason, reviewed_at=CURRENT_TIMESTAMP(3),
                        updated_at=CURRENT_TIMESTAMP(3)
                    WHERE id=:requestId
                    """, new MapSqlParameterSource()
                    .addValue("reviewedBy", admin.userId())
                    .addValue("reason", normalizedReason)
                    .addValue("requestId", requestId));
            createNotification(studentId, "BED_CONFIRMATION_APPROVED",
                    "实际床位核查已通过", "管理员已核查并确认你申报的实际床位。",
                    Map.of("roomId", roomId, "bedId", bedId));
            approved.add(Map.of("requestId", requestId, "studentId", studentId, "bedId", bedId));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roomId", roomId);
        result.put("approved", approved);
        result.put("conflicts", conflicts);
        result.put("approvedCount", approved.size());
        result.put("conflictCount", conflicts.size());
        auditService.success(admin, "BED_CONFIRMATION_ROOM_APPROVE", "ROOM", roomId,
                normalizedReason, null, result);
        return result;
    }

    @Transactional
    public Map<String, Object> reject(
            long requestId,
            String reason,
            CurrentUser admin) {
        Map<String, Object> before = lockRequest(requestId);
        if (!PENDING.equals(String.valueOf(before.get("request_status")))) {
            throw new BusinessException("BED_CONFIRMATION_NOT_PENDING", "该申请已处理", HttpStatus.CONFLICT);
        }
        String normalizedReason = requireReason(reason);
        jdbc.update("""
                UPDATE bed_confirmation_request
                SET request_status='REJECTED', reviewed_by=:reviewedBy,
                    review_reason=:reason, reviewed_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:requestId
                """, new MapSqlParameterSource()
                .addValue("reviewedBy", admin.userId())
                .addValue("reason", normalizedReason)
                .addValue("requestId", requestId));
        createNotification(number(before, "student_id"), "BED_CONFIRMATION_REJECTED",
                "实际床位核查未通过", "管理员未通过你的实际床位申报，请核对后重新提交。",
                Map.of("requestId", requestId, "reason", normalizedReason));
        Map<String, Object> after = request(requestId);
        auditService.success(admin, "BED_CONFIRMATION_REJECT", "BED_CONFIRMATION_REQUEST",
                requestId, normalizedReason, before, after);
        return after;
    }

    private Map<String, Object> currentResidency(long studentId, boolean lock) {
        String sql = """
                SELECT ra.id AS residency_id, ra.student_id, ra.room_id, ra.bed_id,
                       ra.assignment_method, ra.source_selection_mode, ra.assigned_at,
                       db.building_name, floor.floor_number, room.room_number,
                       bed.bed_code, bed.bed_type
                FROM room_assignment ra
                JOIN room ON room.id=ra.room_id
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                JOIN dormitory_building db ON db.id=floor.building_id
                LEFT JOIN bed ON bed.id=ra.bed_id
                WHERE ra.student_id=:studentId AND ra.assignment_status='ACTIVE'
                ORDER BY ra.assigned_at DESC, ra.id DESC LIMIT 1
                """ + (lock ? " FOR UPDATE" : "");
        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of("studentId", studentId));
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private Map<String, Object> roomInfo(long roomId, boolean lock) {
        String sql = """
                SELECT room.id AS room_id, db.building_name, floor.floor_number,
                       room.room_number, room.capacity
                FROM room
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                JOIN dormitory_building db ON db.id=floor.building_id
                WHERE room.id=:roomId
                """ + (lock ? " FOR UPDATE" : "");
        List<Map<String, Object>> rows = jdbc.queryForList(sql, Map.of("roomId", roomId));
        if (rows.isEmpty()) throw new BusinessException("ROOM_NOT_FOUND", "寝室不存在", HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    private List<Map<String, Object>> roomBeds(long roomId) {
        return jdbc.queryForList("""
                SELECT bed.id AS bed_id, bed.bed_code, bed.bed_type,
                       bed.operational_status,
                       CASE WHEN EXISTS(SELECT 1 FROM room_assignment current
                           WHERE current.bed_id=bed.id AND current.assignment_status='ACTIVE')
                           THEN 1 ELSE 0 END AS occupied
                FROM bed WHERE bed.room_id=:roomId
                ORDER BY bed.position_index, bed.id
                """, Map.of("roomId", roomId));
    }

    private Map<String, Object> lockBed(long roomId, long bedId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id AS bed_id, bed_code, bed_type, operational_status
                FROM bed WHERE id=:bedId AND room_id=:roomId FOR UPDATE
                """, Map.of("bedId", bedId, "roomId", roomId));
        if (rows.isEmpty()) throw new BusinessException("BED_NOT_FOUND", "床位不存在或不属于当前寝室", HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    private Map<String, Object> pendingForResidency(long residencyId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT request.*, bed.bed_code, bed.bed_type
                FROM bed_confirmation_request request
                JOIN bed ON bed.id=request.declared_bed_id
                WHERE request.residency_id=:residencyId AND request.request_status='PENDING'
                LIMIT 1
                """, Map.of("residencyId", residencyId));
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private Map<String, Object> lockRequest(long requestId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM bed_confirmation_request WHERE id=:requestId FOR UPDATE",
                Map.of("requestId", requestId));
        if (rows.isEmpty()) throw new BusinessException("BED_CONFIRMATION_NOT_FOUND", "床位核查申请不存在", HttpStatus.NOT_FOUND);
        return rows.getFirst();
    }

    private Map<String, Object> request(long requestId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT request.*, bed.bed_code, bed.bed_type,
                       student.student_number, student.student_name
                FROM bed_confirmation_request request
                JOIN bed ON bed.id=request.declared_bed_id
                JOIN student ON student.id=request.student_id
                WHERE request.id=:requestId
                """, Map.of("requestId", requestId));
        return rows.isEmpty() ? Map.of() : rows.getFirst();
    }

    private void createNotification(
            long studentId, String type, String title, String message,
            Map<String, Object> parameters) {
        jdbc.update("""
                INSERT INTO student_notification
                (student_id,notification_type,title_key,message_key,parameters_json)
                VALUES (:studentId,:type,:title,:message,CAST(:parameters AS JSON))
                """, new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("type", type)
                .addValue("title", title)
                .addValue("message", message)
                .addValue("parameters", toJson(parameters)));
    }

    private String toJson(Map<String, Object> values) {
        return values.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":\"" + String.valueOf(entry.getValue()).replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private String requireReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (value.isEmpty()) throw new BusinessException("BED_CONFIRMATION_REASON_REQUIRED", "请填写操作原因");
        return value;
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
