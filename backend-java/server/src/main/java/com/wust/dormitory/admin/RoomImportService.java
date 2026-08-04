package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RoomImportService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public RoomImportService(NamedParameterJdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> importRows(List<Map<String, String>> rows, CurrentUser operator) {
        int success = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            try {
                applyRow(rows.get(index));
                success++;
            } catch (RuntimeException exception) {
                errors.add(Map.of("row", index + 2, "message", message(exception)));
            }
        }
        Map<String, Object> result = Map.of(
                "total", rows.size(),
                "success", success,
                "failed", errors.size(),
                "errors", errors);
        auditService.success(
                operator,
                "ROOM_IMPORT",
                "ROOM",
                null,
                "批量导入宿舍信息",
                null,
                result);
        return result;
    }

    public void validateRowForImport(Map<String, String> values) {
        RoomRow row = row(values);
        validate(row);
        id("SELECT id FROM campus WHERE enabled=1 ORDER BY id LIMIT 1", Map.of(), "没有可用校区");
        long buildingId = optionalId(
                "SELECT id FROM dormitory_building WHERE building_code=:code",
                Map.of("code", row.buildingCode()));
        if (buildingId == 0) return;
        long floorId = optionalId(
                "SELECT id FROM dormitory_floor WHERE building_id=:buildingId AND floor_number=:floor",
                Map.of("buildingId", buildingId, "floor", row.floorNumber()));
        if (floorId == 0) return;
        long roomId = optionalId(
                "SELECT id FROM room WHERE floor_id=:floorId AND room_number=:roomNumber",
                Map.of("floorId", floorId, "roomNumber", row.roomNumber()));
        if (roomId != 0) ensureExistingRoomMutable(roomId, row.capacity());
    }

    @Transactional
    public RoomApplyResult applyRow(Map<String, String> values) {
        RoomRow row = row(values);
        validate(row);
        long campusId = id(
                "SELECT id FROM campus WHERE enabled=1 ORDER BY id LIMIT 1",
                Map.of(),
                "没有可用校区");

        long buildingId = optionalId(
                "SELECT id FROM dormitory_building WHERE building_code=:code",
                Map.of("code", row.buildingCode()));
        boolean buildingCreated = buildingId == 0;
        if (buildingCreated) {
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO dormitory_building
                    (campus_id, building_code, building_name, gender_restriction, enabled)
                    VALUES (:campusId,:code,:name,:gender,1)
                    """, new MapSqlParameterSource()
                    .addValue("campusId", campusId)
                    .addValue("code", row.buildingCode())
                    .addValue("name", row.buildingName())
                    .addValue("gender", row.gender()),
                    key,
                    new String[]{"id"});
            buildingId = key.getKey().longValue();
        }

        long floorId = optionalId(
                "SELECT id FROM dormitory_floor WHERE building_id=:buildingId AND floor_number=:floor",
                Map.of("buildingId", buildingId, "floor", row.floorNumber()));
        boolean floorCreated = floorId == 0;
        if (floorCreated) {
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO dormitory_floor (building_id,floor_number,enabled)
                    VALUES (:buildingId,:floor,1)
                    """, new MapSqlParameterSource()
                    .addValue("buildingId", buildingId)
                    .addValue("floor", row.floorNumber()),
                    key,
                    new String[]{"id"});
            floorId = key.getKey().longValue();
        }

        long roomId = optionalId(
                "SELECT id FROM room WHERE floor_id=:floorId AND room_number=:roomNumber",
                Map.of("floorId", floorId, "roomNumber", row.roomNumber()));
        boolean roomCreated = roomId == 0;
        Map<String, Object> beforeState = roomCreated ? Map.of() : roomSnapshot(roomId);
        if (roomCreated) {
            GeneratedKeyHolder key = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO room
                    (floor_id,room_number,room_type,capacity,gender_restriction,resident_scope,operational_status,remark)
                    VALUES (:floorId,:number,:type,:capacity,:gender,:scope,:status,:remark)
                    """, roomParameters(row, floorId), key, new String[]{"id"});
            roomId = key.getKey().longValue();
            for (int position = 1; position <= row.capacity(); position++) {
                jdbc.update("""
                        INSERT INTO bed (room_id,bed_code,bed_type,position_index,operational_status)
                        VALUES (:roomId,:code,'LOFT_BED_DESK',:position,'ENABLED')
                        """, Map.of(
                        "roomId", roomId,
                        "code", "B" + position,
                        "position", position));
            }
        } else {
            ensureExistingRoomMutable(roomId, row.capacity());
            MapSqlParameterSource parameters = roomParameters(row, floorId)
                    .addValue("roomId", roomId);
            jdbc.update("""
                    UPDATE room
                    SET room_type=:type,
                        capacity=:capacity,
                        gender_restriction=:gender,
                        resident_scope=:scope,
                        operational_status=:status,
                        remark=:remark,
                        state_version=state_version+1
                    WHERE id=:roomId
                    """, parameters);
        }

        return new RoomApplyResult(
                roomId,
                roomCreated,
                floorCreated ? floorId : null,
                buildingCreated ? buildingId : null,
                beforeState,
                roomSnapshot(roomId));
    }

    private MapSqlParameterSource roomParameters(RoomRow row, long floorId) {
        return new MapSqlParameterSource()
                .addValue("floorId", floorId)
                .addValue("number", row.roomNumber())
                .addValue("type", row.roomType())
                .addValue("capacity", row.capacity())
                .addValue("gender", row.gender())
                .addValue("scope", row.residentScope())
                .addValue("status", row.operationalStatus())
                .addValue("remark", blankToNull(row.remark()));
    }

    private void ensureExistingRoomMutable(long roomId, int expectedCapacity) {
        Integer assigned = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM room_assignment
                WHERE room_id=:roomId AND assignment_status='ACTIVE'
                """, Map.of("roomId", roomId), Integer.class);
        Integer bedCount = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM bed
                WHERE room_id=:roomId AND operational_status='ENABLED'
                """, Map.of("roomId", roomId), Integer.class);
        if ((assigned != null && assigned > 0)
                || bedCount == null
                || bedCount != expectedCapacity) {
            throw new BusinessException(
                    "ROOM_IMPORT_EXISTING_LOCKED",
                    "已有房间存在在住学生或容量与现有床位不一致，请在宿舍编辑器中调整");
        }
    }

    private Map<String, Object> roomSnapshot(long roomId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, floor_id, room_number, room_type, capacity,
                       gender_restriction, resident_scope, operational_status,
                       remark, state_version
                FROM room
                WHERE id=:roomId
                """, Map.of("roomId", roomId));
        if (rows.isEmpty()) throw new BusinessException("ROOM_NOT_FOUND", "宿舍不存在");
        return new LinkedHashMap<>(rows.getFirst());
    }

    private RoomRow row(Map<String, String> values) {
        return new RoomRow(
                normalizeText(value(values, "楼栋编码", "buildingcode")).toUpperCase(Locale.ROOT),
                normalizeText(value(values, "楼栋名称", "buildingname")),
                integer(value(values, "楼层", "floornumber"), "楼层", "层"),
                normalizeText(value(values, "房间号", "roomnumber")).replace(" ", ""),
                normalizeRoomType(defaultValue(value(values, "房型", "roomtype"), "FIVE_PERSON")),
                integer(value(values, "容量", "capacity"), "容量", "人", "床", "个"),
                normalizeGender(defaultValue(value(values, "性别", "gender"), "F")),
                normalizeResidentScope(defaultValue(value(values, "学生类别", "residentscope"), "MIXED")),
                normalizeOperationalStatus(defaultValue(
                        value(values, "运行状态", "operationalstatus"),
                        "ENABLED")),
                normalizeText(value(values, "备注", "remark")));
    }

    private void validate(RoomRow row) {
        if (row.buildingCode().isBlank()
                || row.buildingName().isBlank()
                || row.roomNumber().isBlank()) {
            throw new BusinessException(
                    "ROOM_IMPORT_REQUIRED",
                    "楼栋编码、楼栋名称和房间号不能为空");
        }
        if (row.floorNumber() < 1 || row.floorNumber() > 100) {
            throw new BusinessException("FLOOR_INVALID", "楼层必须为1至100");
        }
        if (row.capacity() < 1 || row.capacity() > 20) {
            throw new BusinessException("ROOM_CAPACITY_INVALID", "容量必须为1至20");
        }
        if (!List.of("FOUR_PERSON", "FIVE_PERSON", "SIX_PERSON", "OTHER")
                .contains(row.roomType())) {
            throw new BusinessException(
                    "ROOM_TYPE_INVALID",
                    "房型必须为四人间、五人间、六人间、其他或对应规范代码");
        }
        if (!List.of("M", "F").contains(row.gender())) {
            throw new BusinessException("ROOM_GENDER_INVALID", "性别必须为男、女、M或F");
        }
        if (!List.of("DOMESTIC_ONLY", "INTERNATIONAL_ONLY", "MIXED")
                .contains(row.residentScope())) {
            throw new BusinessException(
                    "ROOM_SCOPE_INVALID",
                    "学生类别必须为国内生宿舍、国际生宿舍、混住宿舍或对应规范代码");
        }
        if (!List.of("ENABLED", "DISABLED", "MAINTENANCE")
                .contains(row.operationalStatus())) {
            throw new BusinessException(
                    "ROOM_STATUS_INVALID",
                    "运行状态必须为启用、停用、维护或对应规范代码");
        }
        if (row.remark().length() > 500) {
            throw new BusinessException("ROOM_REMARK_TOO_LONG", "备注不能超过500个字符");
        }
    }

    static String normalizeRoomType(String value) {
        return switch (normalizeStatic(value)) {
            case "四人间", "4人间", "FOUR_PERSON" -> "FOUR_PERSON";
            case "五人间", "5人间", "FIVE_PERSON" -> "FIVE_PERSON";
            case "六人间", "6人间", "SIX_PERSON" -> "SIX_PERSON";
            case "其他", "其它", "OTHER" -> "OTHER";
            default -> normalizeStatic(value);
        };
    }

    static String normalizeGender(String value) {
        return switch (normalizeStatic(value)) {
            case "男", "男生", "M", "MALE" -> "M";
            case "女", "女生", "F", "FEMALE" -> "F";
            default -> normalizeStatic(value);
        };
    }

    static String normalizeResidentScope(String value) {
        return switch (normalizeStatic(value)) {
            case "国内生宿舍", "国内生", "DOMESTIC_ONLY" -> "DOMESTIC_ONLY";
            case "国际生宿舍", "国际生", "INTERNATIONAL_ONLY" -> "INTERNATIONAL_ONLY";
            case "混住宿舍", "混住", "不限", "MIXED" -> "MIXED";
            default -> normalizeStatic(value);
        };
    }

    static String normalizeOperationalStatus(String value) {
        return switch (normalizeStatic(value)) {
            case "启用", "正常", "ENABLED" -> "ENABLED";
            case "停用", "禁用", "DISABLED" -> "DISABLED";
            case "维护", "维修", "MAINTENANCE" -> "MAINTENANCE";
            default -> normalizeStatic(value);
        };
    }

    private long optionalId(String sql, Map<String, ?> parameters) {
        List<Long> ids = jdbc.query(sql, parameters, (rs, rowNumber) -> rs.getLong(1));
        return ids.isEmpty() ? 0 : ids.getFirst();
    }

    private long id(String sql, Map<String, ?> parameters, String message) {
        long value = optionalId(sql, parameters);
        if (value == 0) throw new BusinessException("REFERENCE_DATA_REQUIRED", message);
        return value;
    }

    private int integer(String value, String label, String... suffixes) {
        String normalized = normalizeText(value).replace(" ", "");
        for (String suffix : suffixes) normalized = normalized.replace(suffix, "");
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new BusinessException("IMPORT_NUMBER_INVALID", label + "必须为整数");
        }
    }

    private String value(Map<String, String> values, String chinese, String english) {
        String normalizedChinese = chinese.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "");
        return defaultValue(
                values.getOrDefault(normalizedChinese, values.getOrDefault(english, "")),
                "").trim();
    }

    private String normalizeText(String value) {
        return value == null
                ? ""
                : value.replace('\u3000', ' ').trim().replaceAll("\\s+", " ");
    }

    private static String normalizeStatic(String value) {
        return value == null
                ? ""
                : value.replace('\u3000', ' ')
                        .trim()
                        .replaceAll("\\s+", " ")
                        .toUpperCase(Locale.ROOT);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String message(RuntimeException exception) {
        return exception.getMessage() == null ? "导入失败" : exception.getMessage();
    }

    private record RoomRow(
            String buildingCode,
            String buildingName,
            int floorNumber,
            String roomNumber,
            String roomType,
            int capacity,
            String gender,
            String residentScope,
            String operationalStatus,
            String remark) {
    }

    public record RoomApplyResult(
            long roomId,
            boolean roomCreated,
            Long createdFloorId,
            Long createdBuildingId,
            Map<String, Object> beforeState,
            Map<String, Object> afterState) {
    }
}
