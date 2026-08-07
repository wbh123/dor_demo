package com.wust.dormitory.residency;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.mapper.RoomOccupancySnapshotMapper;
import com.wust.dormitory.residency.model.persistence.RoomOccupancySnapshotRow;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ResidencyPolicyService {
    private final NamedParameterJdbcTemplate jdbc;
    private final RoomOccupancySnapshotMapper snapshotMapper;

    public ResidencyPolicyService(
            NamedParameterJdbcTemplate jdbc,
            RoomOccupancySnapshotMapper snapshotMapper) {
        this.jdbc = jdbc;
        this.snapshotMapper = snapshotMapper;
    }

    public Map<String, Object> student(long studentId) {
        return one("""
                SELECT s.id, s.student_number, s.student_name, s.gender,
                       s.student_category, s.nationality_code, s.major_id,
                       s.enrollment_source
                FROM student s WHERE s.id=:id
                """, Map.of("id", studentId), "STUDENT_NOT_FOUND", "学生不存在");
    }

    public Map<String, Object> batch(long batchId) {
        return one("""
                SELECT id, batch_code, batch_name, batch_status, selection_mode,
                       separate_student_categories, start_at, end_at,
                       allow_team, team_min_size, team_max_size
                FROM selection_batch WHERE id=:id
                """, Map.of("id", batchId), "BATCH_NOT_FOUND", "选寝批次不存在");
    }

    public Map<String, Object> room(long roomId, boolean forUpdate) {
        if (!forUpdate) {
            return snapshot(roomId).roomMap();
        }
        return one("""
                SELECT r.id, r.room_number, r.room_type, r.capacity,
                       r.gender_restriction, r.resident_scope,
                       r.operational_status, r.state_version,
                       f.floor_number, b.id AS building_id,
                       b.building_code, b.building_name
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE r.id=:id
                FOR UPDATE
                """, Map.of("id", roomId), "ROOM_NOT_FOUND", "宿舍不存在");
    }

    public Set<Long> roomIdsForBatch(long batchId) {
        List<Long> ids = jdbc.query("""
                SELECT DISTINCT r.id
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
                WHERE rs.id IS NOT NULL OR bs.id IS NOT NULL OR scoped_bed.id IS NOT NULL
                ORDER BY r.id
                """, Map.of("batchId", batchId), (rs, rowNum) -> rs.getLong(1));
        return new LinkedHashSet<>(ids);
    }

    public void requireRoomInBatch(long batchId, long roomId) {
        if (!roomIdsForBatch(batchId).contains(roomId)) {
            throw new BusinessException(
                    "ROOM_OUT_OF_BATCH_SCOPE", "该寝室不属于当前批次可选范围", HttpStatus.FORBIDDEN);
        }
    }

    public void requireBedInBatch(long batchId, long bedId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM bed target_bed
                JOIN room target_room ON target_room.id=target_bed.room_id
                JOIN dormitory_floor target_floor ON target_floor.id=target_room.floor_id
                WHERE target_bed.id=:bedId
                  AND (
                      EXISTS (SELECT 1 FROM batch_bed_scope scope
                              WHERE scope.batch_id=:batchId AND scope.bed_id=target_bed.id)
                      OR EXISTS (SELECT 1 FROM batch_room_scope scope
                                 WHERE scope.batch_id=:batchId AND scope.room_id=target_room.id)
                      OR EXISTS (SELECT 1 FROM batch_building_scope scope
                                 WHERE scope.batch_id=:batchId
                                   AND scope.building_id=target_floor.building_id)
                  )
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId).addValue("bedId", bedId), Integer.class);
        if (count == null || count != 1) {
            throw new BusinessException(
                    "BED_OUT_OF_BATCH_SCOPE", "该床位不属于当前批次开放范围", HttpStatus.FORBIDDEN);
        }
    }

    public void requireRoomLockedByBatch(long batchId, long roomId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM active_batch_room_lock
                WHERE batch_id=:batchId AND room_id=:roomId
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId).addValue("roomId", roomId), Integer.class);
        if (count == null || count != 1) {
            throw new BusinessException(
                    "ROOM_NOT_ACTIVE_FOR_BATCH", "该寝室当前未开放给本批次选择", HttpStatus.CONFLICT);
        }
    }

    public void requireStudentEligibleForRoom(
            Map<String, Object> student,
            Map<String, Object> batch,
            Map<String, Object> room) {
        if (!"ENABLED".equals(String.valueOf(room.get("operational_status")))) {
            throw new BusinessException("ROOM_NOT_AVAILABLE", "该寝室当前不可用");
        }
        if (!String.valueOf(student.get("gender"))
                .equals(String.valueOf(room.get("gender_restriction")))) {
            throw new BusinessException("ROOM_GENDER_MISMATCH", "学生性别与寝室不匹配");
        }
        String category = String.valueOf(student.get("student_category"));
        String scope = String.valueOf(room.get("resident_scope"));
        boolean separate = number(batch.get("separate_student_categories")) == 1;
        if (!roomAllowsCategory(scope, category, separate)) {
            throw new BusinessException(
                    "ROOM_STUDENT_CATEGORY_MISMATCH",
                    separate ? "当前批次要求国内生和国际生分开选寝，该寝室不符合学生类别"
                            : "该寝室的国内生/国际生属性与学生不匹配",
                    HttpStatus.CONFLICT);
        }
    }

    public boolean roomAllowsCategory(String residentScope, String category, boolean separate) {
        if (separate) {
            return ("DOMESTIC".equals(category) && "DOMESTIC_ONLY".equals(residentScope))
                    || ("INTERNATIONAL".equals(category) && "INTERNATIONAL_ONLY".equals(residentScope));
        }
        if ("DOMESTIC".equals(category)) {
            return "DOMESTIC_ONLY".equals(residentScope) || "MIXED".equals(residentScope);
        }
        return "INTERNATIONAL_ONLY".equals(residentScope) || "MIXED".equals(residentScope);
    }

    public int activeResidentCount(long roomId) {
        return snapshot(roomId).activeResidents();
    }

    public int unknownBedResidentCount(long roomId) {
        return snapshot(roomId).unknownBeds();
    }

    public int availableCapacity(long roomId) {
        return snapshot(roomId).remainingCapacity();
    }

    public int availableBedCount(long roomId) {
        return snapshot(roomId).availableBeds();
    }

    public int availableBedCount(long batchId, long roomId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM bed target_bed
                JOIN room target_room ON target_room.id=target_bed.room_id
                JOIN dormitory_floor target_floor ON target_floor.id=target_room.floor_id
                WHERE target_bed.room_id=:roomId
                  AND target_bed.operational_status='ENABLED'
                  AND (
                      EXISTS (SELECT 1 FROM batch_bed_scope scope
                              WHERE scope.batch_id=:batchId AND scope.bed_id=target_bed.id)
                      OR EXISTS (SELECT 1 FROM batch_room_scope scope
                                 WHERE scope.batch_id=:batchId AND scope.room_id=target_room.id)
                      OR EXISTS (SELECT 1 FROM batch_building_scope scope
                                 WHERE scope.batch_id=:batchId
                                   AND scope.building_id=target_floor.building_id)
                  )
                  AND NOT EXISTS (
                      SELECT 1 FROM room_assignment ra
                      WHERE ra.bed_id=target_bed.id AND ra.assignment_status='ACTIVE'
                  )
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId).addValue("roomId", roomId), Integer.class);
        return count == null ? 0 : count;
    }

    public Map<String, Object> requireAvailableBed(long roomId, long bedId) {
        return requireAvailableBed(roomId, bedId, null);
    }

    public Map<String, Object> requireAvailableBed(
            long roomId,
            long bedId,
            Long excludedResidencyId) {
        Map<String, Object> bed = one("""
                SELECT b.id, b.room_id, b.bed_code, b.bed_type, b.operational_status
                FROM bed b WHERE b.id=:bedId AND b.room_id=:roomId
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("bedId", bedId).addValue("roomId", roomId).getValues(),
                "BED_NOT_IN_ROOM", "床位不属于当前寝室");
        if (!"ENABLED".equals(String.valueOf(bed.get("operational_status")))) {
            throw new BusinessException("BED_NOT_AVAILABLE", "床位当前不可用");
        }
        Integer occupied = jdbc.queryForObject("""
                SELECT COUNT(*) FROM room_assignment
                WHERE bed_id=:bedId AND assignment_status='ACTIVE'
                  AND (:excludedId IS NULL OR id<>:excludedId)
                """, new MapSqlParameterSource()
                .addValue("bedId", bedId).addValue("excludedId", excludedResidencyId), Integer.class);
        if (occupied != null && occupied > 0) {
            throw new BusinessException(
                    "BED_ALREADY_OCCUPIED", "该床位已经被其他在住学生确认", HttpStatus.CONFLICT);
        }
        return bed;
    }

    public void requireBatchEligibility(long batchId, long studentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM batch_student_eligibility
                WHERE batch_id=:batchId AND student_id=:studentId
                  AND eligibility_status='ELIGIBLE'
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId).addValue("studentId", studentId), Integer.class);
        if (count == null || count != 1) {
            throw new BusinessException(
                    "STUDENT_NOT_ELIGIBLE", "学生不在当前批次可选名单中", HttpStatus.FORBIDDEN);
        }
    }

    public void requireNoActiveResidency(long studentId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM room_assignment
                WHERE student_id=:studentId AND assignment_status='ACTIVE'
                """, Map.of("studentId", studentId), Integer.class);
        if (count != null && count > 0) {
            throw new BusinessException(
                    "STUDENT_ALREADY_RESIDENT", "学生已经存在有效寝室归属，不能重复分配", HttpStatus.CONFLICT);
        }
    }

    public void requireRoomCapacity(long roomId, int requested) {
        if (requested < 1) {
            throw new BusinessException("ROOM_CAPACITY_REQUEST_INVALID", "申请入住人数必须大于零");
        }
        Map<String, Object> room = room(roomId, true);
        int remaining = number(room.get("capacity")) - activeResidentCount(roomId);
        if (remaining < requested) {
            throw new BusinessException(
                    "ROOM_CAPACITY_INSUFFICIENT",
                    "寝室剩余容量不足，当前可容纳" + Math.max(0, remaining) + "人",
                    HttpStatus.CONFLICT);
        }
    }

    private RoomOccupancySnapshotRow snapshot(long roomId) {
        RoomOccupancySnapshotRow snapshot = snapshotMapper.findSnapshot(roomId);
        if (snapshot == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "宿舍不存在", HttpStatus.NOT_FOUND);
        }
        return snapshot;
    }

    private Map<String, Object> one(
            String sql,
            Map<String, ?> parameters,
            String code,
            String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private int number(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
