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

    public BatchScopeService(NamedParameterJdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public Map<String, Object> get(long batchId) {
        Map<String, Object> batch = currentBatch(batchId, false);
        List<Map<String, Object>> students = jdbc.queryForList("""
                SELECT s.id, s.student_number, s.student_name, s.gender,
                       s.student_category, s.nationality_code, s.degree_level, s.grade_year,
                       s.major_id, m.major_code, m.major_name,
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
                       conflict_batch.id AS conflict_batch_id,
                       conflict_batch.batch_name AS conflict_batch_name,
                       conflict_lock.selection_mode AS conflict_selection_mode,
                       CASE WHEN
                           EXISTS (
                               SELECT 1 FROM batch_room_scope rs
                               WHERE rs.batch_id=:batchId AND rs.room_id=r.id
                           ) OR EXISTS (
                               SELECT 1 FROM batch_building_scope bs
                               WHERE bs.batch_id=:batchId AND bs.building_id=b.id
                           ) OR EXISTS (
                               SELECT 1 FROM batch_bed_scope bds
                               JOIN bed scoped_bed ON scoped_bed.id=bds.bed_id
                               WHERE bds.batch_id=:batchId AND scoped_bed.room_id=r.id
                           )
                           THEN 1 ELSE 0 END AS selected,
                       CASE WHEN b.enabled=1
                                  AND r.operational_status='ENABLED'
                                  AND conflict_lock.room_id IS NULL
                            THEN 1 ELSE 0 END AS selectable,
                       CASE
                         WHEN conflict_lock.room_id IS NOT NULL THEN CONCAT(
                           '正在被批次“', conflict_batch.batch_name, '”用于',
                           CASE WHEN conflict_lock.selection_mode='BED'
                                THEN '选择床位' ELSE '选择寝室' END)
                         WHEN b.enabled<>1 THEN '宿舍楼已停用'
                         WHEN r.operational_status='MAINTENANCE' THEN '房间维护中'
                         WHEN r.operational_status<>'ENABLED' THEN '房间已停用'
                         ELSE NULL
                       END AS disabled_reason
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                LEFT JOIN active_batch_room_lock conflict_lock
                       ON conflict_lock.room_id=r.id
                      AND conflict_lock.batch_id<>:batchId
                LEFT JOIN selection_batch conflict_batch
                       ON conflict_batch.id=conflict_lock.batch_id
                WHERE b.enabled=1
                ORDER BY b.building_code, f.floor_number, r.room_number
                """, Map.of("batchId", batchId));
        rooms.forEach(room -> {
            Object disabledReason = room.get("disabled_reason");
            if (disabledReason != null && !String.valueOf(disabledReason).isBlank()) {
                room.put("operational_status", disabledReason);
            }
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("batchName", batch.get("batch_name"));
        result.put("batchStatus", batch.get("batch_status"));
        result.put("editable", "DRAFT".equals(String.valueOf(batch.get("batch_status"))));
        result.put("selectedStudentCount", selectedCount(students));
        result.put("selectedRoomCount", selectedCount(rooms));
        result.put("students", students);
        result.put("rooms", rooms);
        return result;
    }

    @Transactional
    public Map<String, Object> update(long batchId, UpdateCommand command, CurrentUser operator) {
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
        validateRooms(batchId, roomIds);

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
                    .addValue("studentIds", studentIds));
        }
        if (!roomIds.isEmpty()) {
            jdbc.update("""
                    INSERT INTO batch_room_scope (batch_id, room_id)
                    SELECT :batchId, r.id
                    FROM room r
                    WHERE r.id IN (:roomIds)
                    """, new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("roomIds", roomIds));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batchId", batchId);
        result.put("selectedStudentCount", studentIds.size());
        result.put("selectedRoomCount", roomIds.size());
        auditService.success(operator, "BATCH_SCOPE_UPDATE", "SELECTION_BATCH", batchId,
                "配置批次学生和宿舍范围", null, result);
        return result;
    }

    public void requireReady(long batchId) {
        Integer studentCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM batch_student_eligibility
                WHERE batch_id=:batchId AND eligibility_status='ELIGIBLE'
                """, Map.of("batchId", batchId), Integer.class);
        Integer roomCount = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT r.id)
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                WHERE EXISTS (SELECT 1 FROM batch_room_scope rs
                              WHERE rs.batch_id=:batchId AND rs.room_id=r.id)
                   OR EXISTS (SELECT 1 FROM batch_building_scope bs
                              WHERE bs.batch_id=:batchId AND bs.building_id=f.building_id)
                   OR EXISTS (SELECT 1 FROM batch_bed_scope bds
                              JOIN bed scoped_bed ON scoped_bed.id=bds.bed_id
                              WHERE bds.batch_id=:batchId AND scoped_bed.room_id=r.id)
                """, Map.of("batchId", batchId), Integer.class);
        if (studentCount == null || studentCount == 0) {
            throw new BusinessException("BATCH_STUDENT_SCOPE_REQUIRED",
                    "发布前至少选择一名参与学生", HttpStatus.CONFLICT);
        }
        if (roomCount == null || roomCount == 0) {
            throw new BusinessException("BATCH_ROOM_SCOPE_REQUIRED",
                    "发布前至少选择一间可选宿舍", HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> currentBatch(long batchId, boolean forUpdate) {
        String suffix = forUpdate ? " FOR UPDATE" : "";
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, batch_name, batch_status
                FROM selection_batch WHERE id=:batchId
                """ + suffix, Map.of("batchId", batchId));
        if (rows.isEmpty()) {
            throw new BusinessException("BATCH_NOT_FOUND", "选寝批次不存在", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void validateStudents(List<Long> studentIds) {
        if (studentIds.isEmpty()) return;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM student s JOIN major m ON m.id=s.major_id
                WHERE s.id IN (:studentIds) AND m.enabled=1
                """, Map.of("studentIds", studentIds), Integer.class);
        if (count == null || count != studentIds.size()) {
            throw new BusinessException("BATCH_STUDENT_SCOPE_INVALID",
                    "所选学生中包含不存在或所属专业已停用的学生");
        }
    }

    private void validateRooms(long batchId, List<Long> roomIds) {
        if (roomIds.isEmpty()) return;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE r.id IN (:roomIds)
                  AND b.enabled=1
                  AND r.operational_status='ENABLED'
                  AND NOT EXISTS (
                    SELECT 1 FROM active_batch_room_lock room_lock
                    WHERE room_lock.room_id=r.id
                      AND room_lock.batch_id<>:batchId
                  )
                """, new MapSqlParameterSource()
                .addValue("roomIds", roomIds)
                .addValue("batchId", batchId), Integer.class);
        if (count == null || count != roomIds.size()) {
            throw new BusinessException(
                    "ROOM_ACTIVE_BATCH_CONFLICT",
                    "所选宿舍中包含不存在、停用、维护或正被其他活动批次使用的宿舍",
                    HttpStatus.CONFLICT);
        }
    }

    private int selectedCount(List<Map<String, Object>> items) {
        return (int) items.stream().filter(item -> number(item.get("selected")) == 1).count();
    }

    private List<Long> normalize(List<Long> values) {
        if (values == null || values.isEmpty()) return List.of();
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long value : values) if (value != null && value > 0) unique.add(value);
        return new ArrayList<>(unique);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    public record UpdateCommand(List<Long> studentIds, List<Long> roomIds) {
    }
}
