package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoomManagementService {
    private static final Set<String> BUILDING_GENDERS = Set.of("M", "F", "MIXED");
    private static final Set<String> ROOM_GENDERS = Set.of("M", "F");
    private static final Set<String> EDUCATION_SCOPES = Set.of(
            "UNDERGRADUATE_ONLY", "GRADUATE_ONLY", "MIXED");
    private static final Set<String> RESIDENT_SCOPES = Set.of(
            "DOMESTIC_ONLY", "INTERNATIONAL_ONLY", "MIXED");
    private static final Set<String> OPERATIONAL_STATUSES = Set.of(
            "ENABLED", "DISABLED", "MAINTENANCE");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public RoomManagementService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> buildings() {
        return jdbc.queryForList("""
                SELECT b.id, b.building_code, b.building_name, b.gender_restriction,
                       b.education_level_scope, b.resident_scope, b.enabled, c.campus_name,
                       COUNT(DISTINCT r.id) AS room_count,
                       COUNT(DISTINCT CASE WHEN bed.operational_status<>'RETIRED' THEN bed.id END) AS bed_count
                FROM dormitory_building b
                JOIN campus c ON c.id=b.campus_id
                LEFT JOIN dormitory_floor f ON f.building_id=b.id
                LEFT JOIN room r ON r.floor_id=f.id
                LEFT JOIN bed ON bed.room_id=r.id
                GROUP BY b.id, b.building_code, b.building_name, b.gender_restriction,
                         b.education_level_scope, b.resident_scope, b.enabled, c.campus_name
                ORDER BY b.building_code
                """, Map.of());
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
                       r.education_level_scope, r.resident_scope,
                       b.gender_restriction AS building_gender_restriction,
                       b.education_level_scope AS building_education_level_scope,
                       b.resident_scope AS building_resident_scope,
                       r.operational_status, r.state_version, r.remark,
                       COUNT(CASE WHEN bed.operational_status<>'RETIRED' THEN bed.id END) AS bed_count,
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
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                LEFT JOIN bed ON bed.room_id=r.id
                """ + where + " GROUP BY r.id, b.id, b.building_name, f.floor_number, r.room_number, " +
                "r.room_type, r.capacity, r.gender_restriction, r.education_level_scope, r.resident_scope, " +
                "b.gender_restriction, b.education_level_scope, b.resident_scope, " +
                "r.operational_status, r.state_version, r.remark " +
                "ORDER BY b.building_code, f.floor_number, r.room_number", parameters);
    }

    @Transactional
    public long createBuilding(BuildingCommand command, CurrentUser operator) {
        validateBuildingCommand(command);
        long campusId = requiredId(
                "SELECT id FROM campus WHERE enabled=1 ORDER BY id LIMIT 1",
                Map.of(),
                "CAMPUS_REQUIRED",
                "请先配置可用校区");
        if (count("SELECT COUNT(*) FROM dormitory_building WHERE building_code=:code",
                Map.of("code", command.buildingCode())) > 0) {
            throw new BusinessException("BUILDING_CODE_DUPLICATE", "宿舍楼代码已存在", HttpStatus.CONFLICT);
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO dormitory_building
                (campus_id, building_code, building_name, gender_restriction,
                 education_level_scope, resident_scope, enabled)
                VALUES (:campusId, :code, :name, :gender, :educationScope, :residentScope, 1)
                """, new MapSqlParameterSource()
                .addValue("campusId", campusId)
                .addValue("code", command.buildingCode().trim())
                .addValue("name", command.buildingName().trim())
                .addValue("gender", command.gender())
                .addValue("educationScope", command.educationLevelScope())
                .addValue("residentScope", command.residentScope()), key, new String[]{"id"});
        long buildingId = key.getKey().longValue();
        for (int floor = 1; floor <= command.floorCount(); floor++) {
            jdbc.update("""
                    INSERT INTO dormitory_floor (building_id, floor_number, enabled)
                    VALUES (:buildingId, :floor, 1)
                    """, Map.of("buildingId", buildingId, "floor", floor));
        }
        auditService.success(operator, "BUILDING_CREATE", "BUILDING", buildingId,
                command.reason().trim(), null, command.asAuditMap());
        return buildingId;
    }

    @Transactional
    public long createRoom(RoomCreateCommand command, CurrentUser operator) {
        validateRoomCommand(command.gender(), command.educationLevelScope(),
                command.residentScope(), command.operationalStatus());
        Map<String, Object> building = buildingForValidation(command.buildingId());
        validateRoomWithinBuilding(building, command.gender(), command.educationLevelScope(),
                command.residentScope());
        long floorId = requiredId("""
                SELECT id FROM dormitory_floor
                WHERE building_id=:buildingId AND floor_number=:floorNumber AND enabled=1
                """, Map.of("buildingId", command.buildingId(), "floorNumber", command.floorNumber()),
                "FLOOR_NOT_FOUND", "所选楼层不存在，请先确认宿舍楼楼层设置");
        if (count("SELECT COUNT(*) FROM room WHERE floor_id=:floorId AND room_number=:roomNumber",
                Map.of("floorId", floorId, "roomNumber", command.roomNumber().trim())) > 0) {
            throw new BusinessException("ROOM_NUMBER_DUPLICATE", "该楼层已存在相同宿舍号", HttpStatus.CONFLICT);
        }
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO room
                (floor_id, room_number, room_type, capacity, gender_restriction,
                 education_level_scope, resident_scope, operational_status, remark)
                VALUES (:floorId, :roomNumber, :roomType, :capacity, :gender,
                        :educationScope, :residentScope, :status, :remark)
                """, new MapSqlParameterSource()
                .addValue("floorId", floorId)
                .addValue("roomNumber", command.roomNumber().trim())
                .addValue("roomType", roomTypeForBedCount(command.capacity()))
                .addValue("capacity", command.capacity())
                .addValue("gender", command.gender())
                .addValue("educationScope", command.educationLevelScope())
                .addValue("residentScope", command.residentScope())
                .addValue("status", command.operationalStatus())
                .addValue("remark", normalizeNullable(command.remark()), Types.VARCHAR),
                key, new String[]{"id"});
        long roomId = key.getKey().longValue();
        for (int position = 1; position <= command.capacity(); position++) {
            jdbc.update("""
                    INSERT INTO bed
                    (room_id, bed_code, bed_type, position_index, operational_status)
                    VALUES (:roomId, :bedCode, 'LOFT_BED_DESK', :position, 'ENABLED')
                    """, Map.of("roomId", roomId, "bedCode", "B" + position, "position", position));
        }
        auditService.success(operator, "ROOM_CREATE", "ROOM", roomId,
                command.reason().trim(), null, command.asAuditMap());
        return roomId;
    }

    @Transactional
    public void updateRoom(long roomId, RoomCommand command, CurrentUser operator) {
        Map<String, Object> before = roomForUpdate(roomId);
        int physicalBedCount = count("SELECT COUNT(*) FROM bed WHERE room_id=:id AND operational_status<>'RETIRED'", Map.of("id", roomId));
        if (physicalBedCount < 1 || command.capacity() != physicalBedCount) {
            throw new BusinessException("ROOM_CAPACITY_MISMATCH", "房间容量必须等于当前床位总数");
        }
        validateRoomCommand(command.gender(), command.educationLevelScope(),
                command.residentScope(), command.operationalStatus());
        validateRoomWithinBuilding(before, command.gender(), command.educationLevelScope(),
                command.residentScope());
        validateActiveResidents(roomId, command);

        jdbc.update("""
                UPDATE room SET capacity=:capacity,
                    gender_restriction=:gender,
                    education_level_scope=:educationScope,
                    resident_scope=:residentScope,
                    operational_status=:status,
                    remark=:remark, state_version=state_version+1, version=version+1
                WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("id", roomId)
                .addValue("capacity", physicalBedCount)
                .addValue("gender", command.gender())
                .addValue("educationScope", command.educationLevelScope())
                .addValue("residentScope", command.residentScope())
                .addValue("status", command.operationalStatus())
                .addValue("remark", normalizeNullable(command.remark()), Types.VARCHAR));

        Map<String, Object> after = new LinkedHashMap<>(command.asAuditMap());
        after.put("roomType", before.get("room_type"));
        after.put("capacity", physicalBedCount);
        auditService.success(operator, "ROOM_UPDATE", "ROOM", roomId,
                command.reason().trim(), before, after);
    }

    private void validateActiveResidents(long roomId, RoomCommand command) {
        int incompatibleResidents = count("""
                SELECT COUNT(*)
                FROM room_assignment ra
                JOIN student s ON s.id=ra.student_id
                WHERE ra.room_id=:roomId AND ra.assignment_status='ACTIVE'
                  AND (
                    s.gender<>:gender
                    OR (:residentScope='DOMESTIC_ONLY' AND s.student_category<>'DOMESTIC')
                    OR (:residentScope='INTERNATIONAL_ONLY' AND s.student_category<>'INTERNATIONAL')
                    OR (:educationScope='UNDERGRADUATE_ONLY' AND COALESCE(s.degree_level,'')<>'UNDERGRADUATE')
                    OR (:educationScope='GRADUATE_ONLY' AND COALESCE(s.degree_level,'') NOT IN ('MASTER','DOCTOR','MASTER_DOCTOR'))
                  )
                """, Map.of(
                "roomId", roomId,
                "gender", command.gender(),
                "residentScope", command.residentScope(),
                "educationScope", command.educationLevelScope()));
        if (incompatibleResidents > 0) {
            throw new BusinessException(
                    "ROOM_RESIDENT_SCOPE_CONFLICT",
                    "当前已有在住学生与新的房间属性不一致，请先办理换寝或退宿",
                    HttpStatus.CONFLICT);
        }
    }

    private void validateRoomWithinBuilding(
            Map<String, Object> building,
            String gender,
            String educationScope,
            String residentScope) {
        String buildingGender = String.valueOf(building.get("building_gender_restriction"));
        String buildingEducation = String.valueOf(building.get("building_education_level_scope"));
        String buildingResident = String.valueOf(building.get("building_resident_scope"));
        if (!("MIXED".equals(buildingGender) || buildingGender.equals(gender))
                || !("MIXED".equals(buildingEducation) || buildingEducation.equals(educationScope))
                || !("MIXED".equals(buildingResident) || buildingResident.equals(residentScope))) {
            throw new BusinessException(
                    "ROOM_BUILDING_SCOPE_CONFLICT",
                    "房间的性别、培养层次或学生类别超出了所属宿舍楼允许范围",
                    HttpStatus.CONFLICT);
        }
    }

    private void validateBuildingCommand(BuildingCommand command) {
        if (!BUILDING_GENDERS.contains(command.gender())
                || !EDUCATION_SCOPES.contains(command.educationLevelScope())
                || !RESIDENT_SCOPES.contains(command.residentScope())) {
            throw new BusinessException("BUILDING_SCOPE_INVALID", "请选择有效的宿舍楼属性");
        }
        if (command.floorCount() < 1 || command.floorCount() > 50) {
            throw new BusinessException("BUILDING_FLOOR_COUNT_INVALID", "宿舍楼层数应为1至50层");
        }
    }

    private void validateRoomCommand(
            String gender,
            String educationScope,
            String residentScope,
            String status) {
        if (!ROOM_GENDERS.contains(gender)
                || !EDUCATION_SCOPES.contains(educationScope)
                || !RESIDENT_SCOPES.contains(residentScope)
                || !OPERATIONAL_STATUSES.contains(status)) {
            throw new BusinessException("ROOM_SCOPE_INVALID", "请选择有效的房间属性");
        }
    }

    private Map<String, Object> roomForUpdate(long roomId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.*,
                       b.gender_restriction AS building_gender_restriction,
                       b.education_level_scope AS building_education_level_scope,
                       b.resident_scope AS building_resident_scope
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE r.id=:id
                FOR UPDATE
                """, Map.of("id", roomId));
        if (rows.isEmpty()) {
            throw new BusinessException("ROOM_NOT_FOUND", "房间不存在", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private Map<String, Object> buildingForValidation(long buildingId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id,
                       gender_restriction AS building_gender_restriction,
                       education_level_scope AS building_education_level_scope,
                       resident_scope AS building_resident_scope
                FROM dormitory_building
                WHERE id=:id AND enabled=1
                """, Map.of("id", buildingId));
        if (rows.isEmpty()) {
            throw new BusinessException("BUILDING_NOT_FOUND", "宿舍楼不存在或已停用", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private long requiredId(String sql, Map<String, ?> parameters, String code, String message) {
        List<Long> ids = jdbc.query(sql, parameters, (rs, rowNum) -> rs.getLong(1));
        if (ids.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return ids.getFirst();
    }

    private int count(String sql, Map<String, ?> parameters) {
        Integer result = jdbc.queryForObject(sql, parameters, Integer.class);
        return result == null ? 0 : result;
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private String roomTypeForBedCount(int bedCount) {
        return switch (bedCount) {
            case 4 -> "FOUR_PERSON";
            case 5 -> "FIVE_PERSON";
            case 6 -> "SIX_PERSON";
            default -> "OTHER";
        };
    }

    public record BuildingCommand(
            String buildingCode,
            String buildingName,
            String gender,
            String educationLevelScope,
            String residentScope,
            int floorCount,
            String reason) {
        public Map<String, Object> asAuditMap() {
            return Map.of(
                    "buildingCode", buildingCode,
                    "buildingName", buildingName,
                    "gender", gender,
                    "educationLevelScope", educationLevelScope,
                    "residentScope", residentScope,
                    "floorCount", floorCount);
        }
    }

    public record RoomCreateCommand(
            long buildingId,
            int floorNumber,
            String roomNumber,
            int capacity,
            String gender,
            String educationLevelScope,
            String residentScope,
            String operationalStatus,
            String remark,
            String reason) {
        public Map<String, Object> asAuditMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("buildingId", buildingId);
            values.put("floorNumber", floorNumber);
            values.put("roomNumber", roomNumber);
            values.put("capacity", capacity);
            values.put("gender", gender);
            values.put("educationLevelScope", educationLevelScope);
            values.put("residentScope", residentScope);
            values.put("operationalStatus", operationalStatus);
            values.put("remark", remark);
            return values;
        }
    }

    public record RoomCommand(
            int capacity,
            String gender,
            String educationLevelScope,
            String residentScope,
            String operationalStatus,
            String remark,
            String reason) {
        public Map<String, Object> asAuditMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("capacity", capacity);
            result.put("gender", gender);
            result.put("educationLevelScope", educationLevelScope);
            result.put("residentScope", residentScope);
            result.put("operationalStatus", operationalStatus);
            result.put("remark", remark);
            return result;
        }
    }
}
