package com.wust.dormitory.residency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.common.json.JdbcJsonNormalizer;
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

@Service
public class ResidencyService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyPolicyService policy;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ResidencyService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyPolicyService policy,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> current(long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ra.id AS residency_id, ra.batch_id, ra.student_id,
                       ra.room_id, ra.bed_id, ra.team_id,
                       ra.source_selection_mode, ra.assignment_method,
                       ra.assigned_at, ra.bed_confirmed_at,
                       r.room_number, r.capacity, r.gender_restriction,
                       r.resident_scope, r.operational_status,
                       f.floor_number, db.id AS building_id, db.building_code,
                       db.building_name,
                       b.bed_code, b.bed_type,
                       (ra.bed_id IS NOT NULL) AS bed_confirmed
                FROM room_assignment ra
                JOIN room r ON r.id=ra.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building db ON db.id=f.building_id
                LEFT JOIN bed b ON b.id=ra.bed_id
                WHERE ra.student_id=:studentId AND ra.assignment_status='ACTIVE'
                """, Map.of("studentId", studentId));
        if (rows.isEmpty()) {
            return Map.of("resident", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resident", true);
        result.put("residency", rows.getFirst());
        return result;
    }

    public Map<String, Object> list(Long roomId, String keyword, String bedMappingStatus) {
        StringBuilder where = new StringBuilder(" WHERE ra.assignment_status='ACTIVE' ");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (roomId != null) {
            where.append(" AND ra.room_id=:roomId ");
            parameters.addValue("roomId", roomId);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.student_number LIKE :keyword OR s.student_name LIKE :keyword ")
                    .append("OR r.room_number LIKE :keyword OR db.building_name LIKE :keyword) ");
            parameters.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if ("CONFIRMED".equals(bedMappingStatus)) {
            where.append(" AND ra.bed_id IS NOT NULL ");
        } else if ("UNCONFIRMED".equals(bedMappingStatus)) {
            where.append(" AND ra.bed_id IS NULL ");
        }
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT ra.id AS residency_id, ra.batch_id, ra.student_id,
                       s.student_number, s.student_name, s.gender,
                       s.student_category, s.enrollment_source,
                       ra.room_id, r.room_number, r.resident_scope,
                       f.floor_number, db.id AS building_id, db.building_name,
                       ra.bed_id, b.bed_code, b.bed_type,
                       ra.source_selection_mode, ra.assignment_method,
                       ra.assigned_at, ra.bed_confirmed_at,
                       (ra.bed_id IS NOT NULL) AS bed_confirmed
                FROM room_assignment ra
                JOIN student s ON s.id=ra.student_id
                JOIN room r ON r.id=ra.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building db ON db.id=f.building_id
                LEFT JOIN bed b ON b.id=ra.bed_id
                """ + where + " ORDER BY db.building_code, f.floor_number, r.room_number, s.student_number",
                parameters);
        List<Map<String, Object>> rooms = jdbc.queryForList("""
                SELECT r.id AS room_id, db.building_name, f.floor_number,
                       r.room_number, r.capacity, r.gender_restriction,
                       r.resident_scope, r.operational_status,
                       COUNT(ra.id) AS active_residents,
                       SUM(ra.bed_id IS NOT NULL) AS confirmed_beds,
                       SUM(ra.bed_id IS NULL) AS unconfirmed_beds,
                       GREATEST(r.capacity-COUNT(ra.id),0) AS remaining_capacity
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building db ON db.id=f.building_id
                LEFT JOIN room_assignment ra
                       ON ra.room_id=r.id AND ra.assignment_status='ACTIVE'
                GROUP BY r.id, db.building_name, f.floor_number,
                         r.room_number, r.capacity, r.gender_restriction,
                         r.resident_scope, r.operational_status
                ORDER BY db.building_code, f.floor_number, r.room_number
                """, Map.of());
        return Map.of(
                "items", items,
                "rooms", rooms,
                "total", items.size(),
                "unconfirmed", items.stream().filter(item -> item.get("bed_id") == null).count());
    }

    @Transactional
    public Map<String, Object> assign(
            long studentId,
            long roomId,
            Long bedId,
            Long batchId,
            Long teamId,
            String sourceMode,
            String method,
            String reason,
            CurrentUser operator) {
        Map<String, Object> student = policy.student(studentId);
        Map<String, Object> room = policy.room(roomId, true);
        Map<String, Object> batch = batchId == null
                ? Map.of("separate_student_categories", 0, "selection_mode", "DIRECT")
                : policy.batch(batchId);
        policy.requireNoActiveResidency(studentId);
        policy.requireStudentEligibleForRoom(student, batch, room);
        if (batchId != null) {
            policy.requireRoomInBatch(batchId, roomId);
            policy.requireRoomLockedByBatch(batchId, roomId);
        }
        policy.requireRoomCapacity(roomId, 1);
        if (bedId != null) {
            policy.requireAvailableBed(roomId, bedId);
        }

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO room_assignment
                (batch_id, student_id, room_id, bed_id, team_id,
                 source_selection_mode, assignment_method, assignment_status,
                 assigned_by, assigned_at, bed_confirmed_at)
                VALUES (:batchId, :studentId, :roomId, :bedId, :teamId,
                        :sourceMode, :method, 'ACTIVE', :operatorId,
                        CURRENT_TIMESTAMP(3),
                        CASE WHEN :bedId IS NULL THEN NULL ELSE CURRENT_TIMESTAMP(3) END)
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId)
                .addValue("roomId", roomId)
                .addValue("bedId", bedId)
                .addValue("teamId", teamId)
                .addValue("sourceMode", sourceMode)
                .addValue("method", method)
                .addValue("operatorId", operator.userId()),
                keyHolder,
                new String[]{"id"});
        long residencyId = keyHolder.getKey().longValue();
        appendHistory(
                residencyId,
                studentId,
                roomId,
                bedId,
                bedId == null ? "ROOM_ASSIGNED" : "BED_ASSIGNED",
                operator.userId(),
                reason,
                null,
                Map.of(
                        "batchId", batchId == null ? 0L : batchId,
                        "sourceMode", sourceMode,
                        "method", method,
                        "roomId", roomId,
                        "bedId", bedId == null ? 0L : bedId));
        auditService.success(
                operator,
                bedId == null ? "RESIDENCY_ROOM_ASSIGN" : "RESIDENCY_BED_ASSIGN",
                "ROOM_ASSIGNMENT",
                residencyId,
                requiredReason(reason),
                null,
                Map.of(
                        "studentId", studentId,
                        "roomId", roomId,
                        "bedId", bedId == null ? 0L : bedId,
                        "batchId", batchId == null ? 0L : batchId));
        return residency(residencyId);
    }

    @Transactional
    public Map<String, Object> synchronizeBedAssignment(
            long batchId,
            long studentId,
            long bedId,
            Long teamId,
            long assignedBy,
            String method) {
        Map<String, Object> bed = jdbc.queryForMap("""
                SELECT b.id, b.room_id FROM bed b WHERE b.id=:bedId
                """, Map.of("bedId", bedId));
        long roomId = ((Number) bed.get("room_id")).longValue();
        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id, room_id, bed_id FROM room_assignment
                WHERE student_id=:studentId AND assignment_status='ACTIVE'
                FOR UPDATE
                """, Map.of("studentId", studentId));
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.getFirst();
            if (((Number) row.get("room_id")).longValue() != roomId) {
                throw new BusinessException(
                        "STUDENT_ALREADY_RESIDENT",
                        "学生已经归属其他寝室，不能确认当前床位",
                        HttpStatus.CONFLICT);
            }
            if (row.get("bed_id") != null && ((Number) row.get("bed_id")).longValue() != bedId) {
                throw new BusinessException(
                        "STUDENT_BED_ALREADY_CONFIRMED",
                        "学生已经确认其他床位",
                        HttpStatus.CONFLICT);
            }
            if (row.get("bed_id") == null) {
                policy.requireAvailableBed(roomId, bedId);
                jdbc.update("""
                        UPDATE room_assignment
                        SET bed_id=:bedId, bed_confirmed_at=CURRENT_TIMESTAMP(3),
                            source_selection_mode='BED', assignment_method=:method,
                            updated_at=CURRENT_TIMESTAMP(3)
                        WHERE id=:id
                        """, new MapSqlParameterSource()
                        .addValue("bedId", bedId)
                        .addValue("method", method)
                        .addValue("id", row.get("id")));
            }
            return residency(((Number) row.get("id")).longValue());
        }
        CurrentUser synthetic = new CurrentUser(
                assignedBy, studentId, "system", "系统", "STUDENT");
        return assign(
                studentId,
                roomId,
                bedId,
                batchId,
                teamId,
                "BED",
                method,
                "同步选床结果为跨批次在住事实",
                synthetic);
    }

    @Transactional
    public Map<String, Object> confirmBed(
            long residencyId,
            long bedId,
            String reason,
            CurrentUser operator) {
        Map<String, Object> before = residencyForUpdate(residencyId);
        if (!"ACTIVE".equals(String.valueOf(before.get("assignment_status")))) {
            throw new BusinessException("RESIDENCY_NOT_ACTIVE", "在住记录已经结束");
        }
        long roomId = ((Number) before.get("room_id")).longValue();
        Long previousBedId = nullableLong(before.get("bed_id"));
        if (previousBedId != null && previousBedId == bedId) {
            return residency(residencyId);
        }
        requireBedAvailableForResidency(roomId, bedId, residencyId);
        jdbc.update("""
                UPDATE room_assignment
                SET bed_id=:bedId,
                    bed_confirmed_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, Map.of("bedId", bedId, "id", residencyId));
        Map<String, Object> after = residency(residencyId);
        appendHistory(
                residencyId,
                ((Number) before.get("student_id")).longValue(),
                roomId,
                bedId,
                previousBedId == null ? "BED_CONFIRMED" : "BED_CHANGED",
                operator.userId(),
                reason,
                before,
                after);
        auditService.success(
                operator,
                "RESIDENCY_BED_CONFIRM",
                "ROOM_ASSIGNMENT",
                residencyId,
                requiredReason(reason),
                before,
                after);
        return after;
    }

    @Transactional
    public Map<String, Object> confirmOwnBed(long studentId, long bedId, CurrentUser operator) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM room_assignment
                WHERE student_id=:studentId AND assignment_status='ACTIVE'
                FOR UPDATE
                """, Map.of("studentId", studentId));
        if (rows.isEmpty()) {
            throw new BusinessException("RESIDENCY_NOT_FOUND", "你当前没有有效寝室归属");
        }
        Map<String, Object> residency = rows.getFirst();
        if (residency.get("bed_id") != null) {
            throw new BusinessException(
                    "BED_ALREADY_CONFIRMED",
                    "实际床位已经确认，如需调整请联系管理员",
                    HttpStatus.CONFLICT);
        }
        return confirmBed(
                ((Number) residency.get("id")).longValue(),
                bedId,
                "学生确认本人实际床位",
                operator);
    }

    @Transactional
    public Map<String, Object> end(long residencyId, String reason, CurrentUser operator) {
        Map<String, Object> before = residencyForUpdate(residencyId);
        if (!"ACTIVE".equals(String.valueOf(before.get("assignment_status")))) {
            throw new BusinessException("RESIDENCY_NOT_ACTIVE", "在住记录已经结束");
        }
        jdbc.update("""
                UPDATE room_assignment
                SET assignment_status='ENDED', ended_at=CURRENT_TIMESTAMP(3),
                    end_reason=:reason, updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id
                """, Map.of("reason", requiredReason(reason), "id", residencyId));
        Map<String, Object> after = residency(residencyId);
        appendHistory(
                residencyId,
                ((Number) before.get("student_id")).longValue(),
                ((Number) before.get("room_id")).longValue(),
                nullableLong(before.get("bed_id")),
                "RESIDENCY_ENDED",
                operator.userId(),
                reason,
                before,
                after);
        auditService.success(
                operator,
                "RESIDENCY_END",
                "ROOM_ASSIGNMENT",
                residencyId,
                requiredReason(reason),
                before,
                after);
        return after;
    }

    public Map<String, Object> residency(long residencyId) {
        return jdbc.queryForMap("""
                SELECT ra.*, s.student_number, s.student_name, s.gender,
                       s.student_category, r.room_number, r.resident_scope,
                       f.floor_number, db.building_name,
                       b.bed_code, b.bed_type
                FROM room_assignment ra
                JOIN student s ON s.id=ra.student_id
                JOIN room r ON r.id=ra.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building db ON db.id=f.building_id
                LEFT JOIN bed b ON b.id=ra.bed_id
                WHERE ra.id=:id
                """, Map.of("id", residencyId));
    }


    private void requireBedAvailableForResidency(
            long roomId,
            long bedId,
            long residencyId) {
        List<Map<String, Object>> beds = jdbc.queryForList("""
                SELECT id, room_id, operational_status
                FROM bed
                WHERE id=:bedId
                FOR UPDATE
                """, Map.of("bedId", bedId));
        if (beds.isEmpty()) {
            throw new BusinessException(
                    "BED_NOT_FOUND",
                    "床位不存在",
                    HttpStatus.NOT_FOUND);
        }
        Map<String, Object> bed = beds.getFirst();
        if (((Number) bed.get("room_id")).longValue() != roomId) {
            throw new BusinessException(
                    "BED_NOT_IN_RESIDENCY_ROOM",
                    "所选床位不属于学生当前寝室",
                    HttpStatus.CONFLICT);
        }
        if (!"ENABLED".equals(String.valueOf(bed.get("operational_status")))) {
            throw new BusinessException(
                    "BED_NOT_AVAILABLE",
                    "所选床位当前不可用",
                    HttpStatus.CONFLICT);
        }
        Integer occupied = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM room_assignment
                WHERE bed_id=:bedId
                  AND assignment_status='ACTIVE'
                  AND id<>:residencyId
                """, new MapSqlParameterSource()
                .addValue("bedId", bedId)
                .addValue("residencyId", residencyId), Integer.class);
        if (occupied != null && occupied > 0) {
            throw new BusinessException(
                    "BED_ALREADY_OCCUPIED",
                    "所选床位已被其他在住学生占用",
                    HttpStatus.CONFLICT);
        }
    }

    private Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private Map<String, Object> residencyForUpdate(long residencyId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM room_assignment WHERE id=:id FOR UPDATE",
                Map.of("id", residencyId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "RESIDENCY_NOT_FOUND",
                    "在住记录不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void appendHistory(
            Long assignmentId,
            long studentId,
            long roomId,
            Long bedId,
            String eventType,
            Long operatorId,
            String reason,
            Object previous,
            Object current) {
        jdbc.update("""
                INSERT INTO room_assignment_history
                (room_assignment_id, student_id, room_id, bed_id, event_type,
                 operator_user_id, reason, previous_data, current_data, occurred_at)
                VALUES (:assignmentId, :studentId, :roomId, :bedId, :eventType,
                        :operatorId, :reason, CAST(:previous AS JSON),
                        CAST(:current AS JSON), CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("assignmentId", assignmentId)
                .addValue("studentId", studentId)
                .addValue("roomId", roomId)
                .addValue("bedId", bedId)
                .addValue("eventType", eventType)
                .addValue("operatorId", operatorId)
                .addValue("reason", requiredReason(reason))
                .addValue("previous", json(previous))
                .addValue("current", json(current)));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(JdbcJsonNormalizer.normalize(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("在住历史序列化失败", exception);
        }
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("CHANGE_REASON_REQUIRED", "必须填写操作原因");
        }
        return reason.trim();
    }
}
