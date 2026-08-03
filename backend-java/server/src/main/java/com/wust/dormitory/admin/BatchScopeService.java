package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class BatchScopeService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public BatchScopeService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public Map<String, Object> get(long batchId) {
        Map<String, Object> batch = currentBatch(batchId, false);
        List<Map<String, Object>> students = jdbc.queryForList("""
                SELECT s.id, s.student_number, s.student_name, s.gender,
                       s.student_category, s.major_id, m.major_code, m.major_name,
                       CASE WHEN e.id IS NULL THEN 0 ELSE 1 END AS selected
                FROM student s
                JOIN major m ON m.id=s.major_id
                LEFT JOIN batch_student_eligibility e
                       ON e.batch_id=:batchId
                      AND e.student_id=s.id
                      AND e.eligibility_status='ELIGIBLE'
                WHERE m.enabled=1
                ORDER BY m.major_code, s.student_number
                """, Map.of("batchId", batchId));
        List<Map<String, Object>> rooms = jdbc.queryForList("""
                SELECT r.id, b.id AS building_id, b.building_code, b.building_name,
                       f.floor_number, r.room_number, r.room_type, r.capacity,
                       r.gender_restriction, r.resident_scope, r.operational_status,
                       CASE WHEN rs.id IS NOT NULL OR bs.id IS NOT NULL OR scoped_bed.id IS NOT NULL
                            THEN 1 ELSE 0 END AS selected,
                       CASE WHEN b.enabled=1 AND r.operational_status='ENABLED'
                            THEN 1 ELSE 0 END AS selectable
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                LEFT JOIN batch_room_scope rs
                       ON rs.batch_id=:batchId AND rs.room_id=r.id
                LEFT JOIN batch_building_scope bs
                       ON bs.batch_id=:batchId AND bs.building_id=b.id
                LEFT JOIN batch_bed_scope bds
                       ON bds.batch_id=:batchId
                LEFT JOIN bed scoped_bed
                       ON scoped_bed.id=bds.bed_id AND scoped_bed.room_id=r.id
                WHERE b.enabled=1
                GROUP BY r.id, b.id, b.building_code, b.building_name,
                         f.floor_number, r.room_number, r.room_type, r.capacity,
                         r.gender_restriction, r.resident_scope, r.operational_status,
                         rs.id, bs.id, scoped_bed.id, b.enabled
                ORDER BY b.building_code, f.floor_number, r.room_number
                """, Map.of("batchId", batchId));

        int selectedStudents = selectedCount(students);
        int selectedRooms = selectedCount(rooms);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("batchName", batch.get("batch_name"));
        result.put("batchStatus", batch.get("batch_status"));
        result.put("editable", "DRAFT".equals(String.valueOf(batch.get("batch_status"))));
        result.put("selectedStudentCount", selectedStudents);
        result.put("selectedRoomCount", selectedRooms);
        result.put("students", students);
        result.put("rooms", rooms);
        return result;
    }

    @Transactional
    public Map<String, Object> update(
            long batchId,
            UpdateCommand command,
            CurrentUser operator) {
        Map<String, Object> batch = currentBatch(batchId, true);
        if (!"DRAFT".equals(String.valueOf(batch.get("batch_status")))) {
            throw new BusinessException(
                    "BATCH_SCOPE_LOCKED",
                    "只有草稿批次可以修改学生和宿舍范围",
                    HttpStatus.CONFLICT);
        }

        List<Long> studentIds = normalize(command.studentIds());
        List<Long> roomIds = normalize(command.roomIds());
        validateStudents(studentIds);
        validateRooms(roomIds);

        Map<String, Object> parameters = Map.of("batchId", batchId);
        jdbc.update("DELETE FROM batch_bed_scope WHERE batch_id=:batchId", parameters);
        jdbc.update("DELETE FROM batch_room_scope WHERE batch_id=:batchId", parameters);
        jdbc.update("DELETE FROM batch_building_scope WHERE batch_id=:batchId", parameters);
        jdbc.update("DELETE FROM batch_student_eligibility WHERE batch_id=:batchId", parameters);

        if (!studentIds.isEmpty()) {
            jdbc.update("""
                    INSERT INTO batch_student_eligibility
                    (batch_id, student_id, eligibility_status)
                    SELECT :batchId, s.id, 'ELIGIBLE'
                    FROM student s
                    WHERE s.id IN (:studentIds)
                    """, new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("studentIds", studentIds)
                    .getValues());
        }
        if (!roomIds.isEmpty()) {
            jdbc.update("""
                    INSERT INTO batch_room_scope (batch_id, room_id)
                    SELECT :batchId, r.id
                    FROM room r
                    WHERE r.id IN (:roomIds)
                    """, new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("roomIds", roomIds)
                    .getValues());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("selectedStudentCount", studentIds.size());
        result.put("selectedRoomCount", roomIds.size());
        auditService.success(
                operator,
                "BATCH_SCOPE_UPDATE",
                "SELECTION_BATCH",
                batchId,
                "配置批次学生和宿舍范围",
                null,
                result);
        return result;
    }

    public void requireReady(long batchId) {
        Integer studentCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM batch_student_eligibility
                WHERE batch_id=:batchId AND eligibility_status='ELIGIBLE'
                """, Map.of("batchId", batchId), Integer.class);
        Integer roomCount = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT r.id)
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                LEFT JOIN batch_room_scope rs
                       ON rs.batch_id=:batchId AND rs.room_id=r.id
                LEFT JOIN batch_building_scope bs
                       ON bs.batch_id=:batchId AND bs.building_id=f.building_id
                LEFT JOIN batch_bed_scope bds
                       ON bds.batch_id=:batchId
                LEFT JOIN bed scoped_bed
                       ON scoped_bed.id=bds.bed_id AND scoped_bed.room_id=r.id
                WHERE rs.id IS NOT NULL OR bs.id IS NOT NULL OR scoped_bed.id IS NOT NULL
                """, Map.of("batchId", batchId), Integer.class);
        if (studentCount == null || studentCount == 0) {
            throw new BusinessException(
                    "BATCH_STUDENT_SCOPE_REQUIRED",
                    "发布前至少选择一名参与学生",
                    HttpStatus.CONFLICT);
        }
        if (roomCount == null || roomCount == 0) {
            throw new BusinessException(
                    "BATCH_ROOM_SCOPE_REQUIRED",
                    "发布前至少选择一间可选宿舍",
                    HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> currentBatch(long batchId, boolean forUpdate) {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, batch_name, batch_status
                FROM selection_batch
                WHERE id=:batchId
                """ + suffix, Map.of("batchId", batchId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "BATCH_NOT_FOUND",
                    "选寝批次不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void validateStudents(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM student s
                JOIN major m ON m.id=s.major_id
                WHERE s.id IN (:studentIds) AND m.enabled=1
                """, Map.of("studentIds", studentIds), Integer.class);
        if (count == null || count != studentIds.size()) {
            throw new BusinessException(
                    "BATCH_STUDENT_SCOPE_INVALID",
                    "所选学生中包含不存在或所属专业已停用的学生");
        }
    }

    private void validateRooms(List<Long> roomIds) {
        if (roomIds.isEmpty()) {
            return;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE r.id IN (:roomIds)
                  AND b.enabled=1
                  AND r.operational_status='ENABLED'
                """, Map.of("roomIds", roomIds), Integer.class);
        if (count == null || count != roomIds.size()) {
            throw new BusinessException(
                    "BATCH_ROOM_SCOPE_INVALID",
                    "所选宿舍中包含不存在、已停用或维护中的宿舍");
        }
    }

    private int selectedCount(List<Map<String, Object>> items) {
        return (int) items.stream()
                .filter(item -> number(item.get("selected")) == 1)
                .count();
    }

    private List<Long> normalize(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long value : values) {
            if (value != null && value > 0) {
                unique.add(value);
            }
        }
        return new ArrayList<>(unique);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    public record UpdateCommand(List<Long> studentIds, List<Long> roomIds) {
    }
}
