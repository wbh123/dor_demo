package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoomManagementService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public RoomManagementService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> rooms(Long buildingId, String gender) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (buildingId != null) {
            where.append(" AND b.id=:buildingId ");
            parameters.addValue("buildingId", buildingId);
        }
        if (gender != null && !gender.isBlank()) {
            where.append(" AND r.gender_restriction=:gender ");
            parameters.addValue("gender", gender);
        }
        return jdbc.queryForList("""
                SELECT r.id, b.id AS building_id, b.building_name, f.floor_number,
                       r.room_number, r.room_type, r.capacity, r.gender_restriction,
                       r.resident_scope, r.operational_status, r.state_version, r.remark,
                       COUNT(bed.id) AS bed_count,
                       COALESCE(SUM(bed.operational_status='ENABLED'), 0) AS enabled_bed_count,
                       COALESCE(SUM(bed.operational_status='DISABLED'), 0) AS disabled_bed_count,
                       COALESCE(SUM(bed.operational_status='MAINTENANCE'), 0) AS maintenance_bed_count,
                       (SELECT COUNT(*) FROM room_assignment ra
                        WHERE ra.room_id=r.id AND ra.assignment_status='ACTIVE') AS active_resident_count,
                       (SELECT COUNT(*) FROM room_assignment ra
                        WHERE ra.room_id=r.id AND ra.assignment_status='ACTIVE'
                          AND ra.bed_id IS NOT NULL) AS confirmed_bed_count,
                       (SELECT COUNT(*) FROM room_assignment ra
                        WHERE ra.room_id=r.id AND ra.assignment_status='ACTIVE'
                          AND ra.bed_id IS NULL) AS unconfirmed_bed_count,
                       GREATEST(r.capacity-(SELECT COUNT(*) FROM room_assignment ra
                        WHERE ra.room_id=r.id AND ra.assignment_status='ACTIVE'),0) AS remaining_capacity
                FROM room r JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                LEFT JOIN bed ON bed.room_id=r.id
                """ + where + " GROUP BY r.id, b.id, b.building_name, f.floor_number, r.room_number, " +
                "r.room_type, r.capacity, r.gender_restriction, r.resident_scope, " +
                "r.operational_status, r.state_version, r.remark " +
                "ORDER BY b.building_code, f.floor_number, r.room_number", parameters);
    }

    @Transactional
    public void updateRoom(long roomId, RoomCommand command, CurrentUser operator) {
        Map<String, Object> before = roomForUpdate(roomId);
        int physicalBedCount = count(
                "SELECT COUNT(*) FROM bed WHERE room_id=:id",
                Map.of("id", roomId));
        if (physicalBedCount < 1) {
            throw new BusinessException(
                    "ROOM_CAPACITY_MISMATCH",
                    "房间尚未配置床位，不能启用房间编辑");
        }
        if (command.capacity() != physicalBedCount) {
            throw new BusinessException(
                    "ROOM_CAPACITY_MISMATCH",
                    "房间容量必须等于当前床位总数");
        }
        if (!List.of("DOMESTIC_ONLY", "INTERNATIONAL_ONLY", "MIXED")
                .contains(command.residentScope())) {
            throw new BusinessException(
                    "ROOM_RESIDENT_SCOPE_INVALID",
                    "请选择国内生宿舍、国际生宿舍或混住宿舍");
        }

        int incompatibleResidents = count("""
                SELECT COUNT(*)
                FROM room_assignment ra
                JOIN student s ON s.id=ra.student_id
                WHERE ra.room_id=:roomId AND ra.assignment_status='ACTIVE'
                  AND (
                    (:scope='DOMESTIC_ONLY' AND s.student_category<>'DOMESTIC')
                    OR (:scope='INTERNATIONAL_ONLY' AND s.student_category<>'INTERNATIONAL')
                  )
                """, Map.of("roomId", roomId, "scope", command.residentScope()));
        if (incompatibleResidents > 0) {
            throw new BusinessException(
                    "ROOM_RESIDENT_SCOPE_CONFLICT",
                    "当前已有在住学生与新的宿舍属性不一致，请先办理换寝或退宿",
                    HttpStatus.CONFLICT);
        }

        jdbc.update("""
                UPDATE room SET capacity=:capacity,
                    gender_restriction=:gender,
                    resident_scope=:residentScope,
                    operational_status=:status,
                    remark=:remark, state_version=state_version+1, version=version+1
                WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("id", roomId)
                .addValue("capacity", physicalBedCount)
                .addValue("gender", command.gender())
                .addValue("residentScope", command.residentScope())
                .addValue("status", command.operationalStatus())
                .addValue("remark", normalizeNullable(command.remark()), Types.VARCHAR));

        Map<String, Object> after = new LinkedHashMap<>(command.asAuditMap());
        after.put("roomType", before.get("room_type"));
        after.put("capacity", physicalBedCount);
        auditService.success(
                operator,
                "ROOM_UPDATE",
                "ROOM",
                roomId,
                command.reason().trim(),
                before,
                after);
    }

    private Map<String, Object> roomForUpdate(long roomId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM room WHERE id=:id FOR UPDATE",
                Map.of("id", roomId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "ROOM_NOT_FOUND",
                    "房间不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private int count(String sql, Map<String, ?> parameters) {
        Integer result = jdbc.queryForObject(sql, parameters, Integer.class);
        return result == null ? 0 : result;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record RoomCommand(
            int capacity,
            String gender,
            String residentScope,
            String operationalStatus,
            String remark,
            String reason) {

        public Map<String, Object> asAuditMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("capacity", capacity);
            result.put("gender", gender);
            result.put("residentScope", residentScope);
            result.put("operationalStatus", operationalStatus);
            result.put("remark", remark);
            return result;
        }
    }
}
