package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminStudentResidencyAdjustmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyService residencyService;

    public AdminStudentResidencyAdjustmentService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyService residencyService) {
        this.jdbc = jdbc;
        this.residencyService = residencyService;
    }

    public Map<String, Object> context(long studentId) {
        Map<String, Object> student = student(studentId);
        Map<String, Object> currentResult = residencyService.current(studentId);
        @SuppressWarnings("unchecked")
        Map<String, Object> current = Boolean.TRUE.equals(currentResult.get("resident"))
                ? (Map<String, Object>) currentResult.get("residency")
                : Map.of();
        Long currentRoomId = numberOrNull(current.get("room_id"));
        Long currentBedId = numberOrNull(current.get("bed_id"));

        List<Map<String, Object>> availableBeds = availableBeds(
                student,
                currentRoomId,
                currentBedId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("studentNumber", student.get("student_number"));
        result.put("studentName", student.get("student_name"));
        result.put("gender", student.get("gender"));
        result.put("studentCategory", student.get("student_category"));
        result.put("resident", !current.isEmpty());
        result.put("currentResidency", current);
        result.put("availableBeds", availableBeds);
        return result;
    }

    @Transactional
    public Map<String, Object> adjust(
            long studentId,
            long bedId,
            String reason,
            CurrentUser operator) {
        String normalizedReason = requiredReason(reason);
        Map<String, Object> student = student(studentId);
        Map<String, Object> currentResult = residencyService.current(studentId);
        @SuppressWarnings("unchecked")
        Map<String, Object> current = Boolean.TRUE.equals(currentResult.get("resident"))
                ? (Map<String, Object>) currentResult.get("residency")
                : Map.of();
        Long currentRoomId = numberOrNull(current.get("room_id"));
        Long currentBedId = numberOrNull(current.get("bed_id"));
        if (currentBedId != null && currentBedId == bedId) {
            throw new BusinessException(
                    "RESIDENCY_ADJUSTMENT_NO_CHANGE",
                    "目标床位与当前床位相同，请选择其他床位",
                    HttpStatus.CONFLICT);
        }

        Map<String, Object> target = availableBeds(student, currentRoomId, currentBedId).stream()
                .filter(item -> number(item.get("bed_id")) == bedId)
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "RESIDENCY_ADJUSTMENT_TARGET_UNAVAILABLE",
                        "目标床位已不可用，可能被占用、处于活动锁定或不符合学生住宿条件",
                        HttpStatus.CONFLICT));

        if (!current.isEmpty()) {
            residencyService.end(
                    number(current.get("residency_id")),
                    "管理员调整住宿：" + normalizedReason,
                    operator);
        }
        Map<String, Object> assignment = residencyService.assign(
                studentId,
                number(target.get("room_id")),
                bedId,
                null,
                null,
                "DIRECT",
                "MANUAL_ADJUSTMENT",
                normalizedReason,
                operator);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("studentId", studentId);
        result.put("previousResidency", current);
        result.put("assignment", assignment);
        result.put("moved", !current.isEmpty());
        return result;
    }

    private Map<String, Object> student(long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, student_number, student_name, gender, student_category
                FROM student
                WHERE id=:studentId
                """, Map.of("studentId", studentId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "STUDENT_NOT_FOUND",
                    "学生不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private List<Map<String, Object>> availableBeds(
            Map<String, Object> student,
            Long currentRoomId,
            Long currentBedId) {
        return jdbc.queryForList("""
                SELECT bed.id AS bed_id, bed.room_id, bed.bed_code, bed.bed_type,
                       room.room_number, room.capacity, room.resident_scope,
                       floor.floor_number,
                       building.id AS building_id, building.building_code,
                       building.building_name,
                       CONCAT(building.building_name, ' ', room.room_number, ' · ', bed.bed_code)
                           AS display_name
                FROM bed
                JOIN room ON room.id=bed.room_id
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                JOIN dormitory_building building ON building.id=floor.building_id
                WHERE building.enabled=1
                  AND room.operational_status='ENABLED'
                  AND bed.operational_status='ENABLED'
                  AND room.gender_restriction=:gender
                  AND (
                    (:studentCategory='DOMESTIC' AND room.resident_scope IN ('DOMESTIC_ONLY','MIXED'))
                    OR (:studentCategory='INTERNATIONAL' AND room.resident_scope IN ('INTERNATIONAL_ONLY','MIXED'))
                  )
                  AND (:currentBedId IS NULL OR bed.id<>:currentBedId)
                  AND NOT EXISTS (
                    SELECT 1 FROM room_assignment occupied
                    WHERE occupied.bed_id=bed.id
                      AND occupied.assignment_status='ACTIVE'
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM active_batch_room_lock room_lock
                    WHERE room_lock.room_id=room.id
                  )
                  AND (
                    room.id=:currentRoomId
                    OR (SELECT COUNT(*) FROM room_assignment resident
                        WHERE resident.room_id=room.id
                          AND resident.assignment_status='ACTIVE') < room.capacity
                  )
                ORDER BY building.building_code, floor.floor_number,
                         room.room_number, bed.bed_code
                """, new MapSqlParameterSource()
                .addValue("gender", student.get("gender"))
                .addValue("studentCategory", student.get("student_category"))
                .addValue("currentRoomId", currentRoomId == null ? -1L : currentRoomId)
                .addValue("currentBedId", currentBedId));
    }

    private String requiredReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 2 || normalized.length() > 500) {
            throw new BusinessException(
                    "RESIDENCY_ADJUSTMENT_REASON_INVALID",
                    "调整原因长度必须为2至500个字符");
        }
        return normalized;
    }

    private long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private Long numberOrNull(Object value) {
        return value == null ? null : number(value);
    }
}
