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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoomLayoutService {
    public static final String DEFAULT_LAYOUT = "DEFAULT_LAYOUT";
    public static final String CUSTOM_LAYOUT = "CUSTOM_LAYOUT";

    private static final int MAX_ROOM_CAPACITY = 8;
    private static final double MIN_X = -5.2;
    private static final double MAX_X = 5.2;
    private static final double MIN_Z = -3.5;
    private static final double MAX_Z = 3.5;
    private static final Set<Integer> ROTATIONS = Set.of(0, 90, 180, 270);
    private static final Set<String> UNIT_TYPES = Set.of("LOFT_BED_DESK", "BUNK");

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public RoomLayoutService(NamedParameterJdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public Map<String, Object> getLayout(long roomId) {
        Map<String, Object> room = one("""
                SELECT r.id, r.room_number, r.room_type, r.capacity,
                       r.version AS room_version, r.state_version,
                       f.floor_number, b.building_name
                FROM room r
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE r.id=:roomId
                """, Map.of("roomId", roomId), "ROOM_NOT_FOUND", "房间不存在");

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bed.id, bed.bed_code, bed.bed_type, bed.position_index,
                       bed.bed_frame_id, bed.operational_status,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM bed_assignment assignment
                           WHERE assignment.bed_id=bed.id
                       ) THEN 1 ELSE 0 END AS occupied,
                       layout.layout_x, layout.layout_z, layout.rotation_degrees,
                       CASE WHEN layout.bed_id IS NULL THEN 0 ELSE 1 END AS custom_layout
                FROM bed
                LEFT JOIN room_bed_layout layout ON layout.bed_id=bed.id
                WHERE bed.room_id=:roomId
                ORDER BY bed.position_index, bed.id
                """, Map.of("roomId", roomId));

        Set<Long> genuineBunkFrames = genuineBunkFrameIds(rows);
        boolean hasCustom = false;
        List<Map<String, Object>> beds = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> bed = new LinkedHashMap<>(row);
            boolean custom = ((Number) row.get("custom_layout")).intValue() == 1;
            hasCustom |= custom;
            if (!custom) {
                DefaultPlacement placement = defaultPlacement(
                        String.valueOf(row.get("bed_type")),
                        ((Number) row.get("position_index")).intValue());
                bed.put("layout_x", placement.x());
                bed.put("layout_z", placement.z());
                bed.put("rotation_degrees", placement.rotationDegrees());
            }
            Object frameValue = row.get("bed_frame_id");
            boolean genuineBunk = frameValue != null
                    && genuineBunkFrames.contains(number(frameValue));
            bed.put("layout_unit_type", genuineBunk ? "BUNK" : "LOFT_BED_DESK");
            beds.add(bed);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("room", room);
        result.put("beds", beds);
        result.put("layout_source", hasCustom ? CUSTOM_LAYOUT : DEFAULT_LAYOUT);
        result.put("maximum_capacity", MAX_ROOM_CAPACITY);
        return result;
    }

    @Transactional
    public Map<String, Object> updateLayout(
            long roomId,
            LayoutCommand command,
            CurrentUser operator) {
        Map<String, Object> room = one("""
                SELECT id, version, state_version, capacity
                FROM room
                WHERE id=:roomId
                FOR UPDATE
                """, Map.of("roomId", roomId), "ROOM_NOT_FOUND", "房间不存在");

        long currentVersion = ((Number) room.get("version")).longValue();
        if (currentVersion != command.expectedRoomVersion()) {
            throw new BusinessException(
                    "ROOM_LAYOUT_VERSION_CONFLICT",
                    "房间布局已被其他管理员修改，请重新加载后再保存",
                    HttpStatus.CONFLICT);
        }
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException("ROOM_LAYOUT_REASON_REQUIRED", "请填写布局修改原因");
        }

        List<Map<String, Object>> roomBeds = lockRoomBeds(roomId);
        List<BedUnit> units = buildUnits(roomBeds);
        validateUnitSet(units, command.beds());
        validateItems(command.beds());

        Map<String, Object> before = getLayout(roomId);
        Map<Long, LayoutItem> itemById = new HashMap<>();
        for (LayoutItem item : command.beds()) {
            itemById.put(item.bedId(), item);
        }

        int capacity = roomBeds.size();
        for (BedUnit unit : units) {
            LayoutItem item = itemById.get(unit.representativeBedId());
            if (unit.occupied() && !unit.unitType().equals(item.bedType())) {
                throw new BusinessException(
                        "BED_TYPE_OCCUPIED",
                        "非空床位不能修改床位类型",
                        HttpStatus.CONFLICT);
            }
            if ("BUNK".equals(unit.unitType()) && "LOFT_BED_DESK".equals(item.bedType())) {
                throw new BusinessException(
                        "BUNK_COLLAPSE_NOT_SUPPORTED",
                        "上下铺不能直接合并为上床下桌，请停用多余床位后再调整",
                        HttpStatus.CONFLICT);
            }
            if ("LOFT_BED_DESK".equals(unit.unitType()) && "BUNK".equals(item.bedType())) {
                if (capacity >= MAX_ROOM_CAPACITY) {
                    throw new BusinessException(
                            "ROOM_CAPACITY_LIMIT",
                            "房间最多只能配置8个床位",
                            HttpStatus.CONFLICT);
                }
                splitLoftIntoBunk(roomId, unit, item, operator.userId());
                capacity++;
            } else {
                savePlacement(unit.beds(), item, operator.userId());
            }
        }

        String roomType = roomTypeForBedCount(capacity);
        int updated = jdbc.update("""
                UPDATE room
                SET room_type=:roomType, capacity=:capacity,
                    version=version+1, state_version=state_version+1
                WHERE id=:roomId AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("roomId", roomId)
                .addValue("roomType", roomType)
                .addValue("capacity", capacity)
                .addValue("expectedVersion", command.expectedRoomVersion()));
        if (updated != 1) {
            throw new BusinessException(
                    "ROOM_LAYOUT_VERSION_CONFLICT",
                    "房间布局已被其他管理员修改，请重新加载后再保存",
                    HttpStatus.CONFLICT);
        }

        Map<String, Object> after = getLayout(roomId);
        auditService.success(
                operator,
                "ROOM_LAYOUT_UPDATE",
                "ROOM",
                roomId,
                command.reason().trim(),
                before,
                after);
        return after;
    }

    private List<Map<String, Object>> lockRoomBeds(long roomId) {
        return jdbc.queryForList("""
                SELECT bed.id, bed.bed_code, bed.bed_type, bed.position_index,
                       bed.bed_frame_id,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM bed_assignment assignment
                           WHERE assignment.bed_id=bed.id
                       ) THEN 1 ELSE 0 END AS occupied
                FROM bed
                WHERE bed.room_id=:roomId
                ORDER BY bed.position_index, bed.id
                FOR UPDATE
                """, Map.of("roomId", roomId));
    }

    private List<BedUnit> buildUnits(List<Map<String, Object>> roomBeds) {
        Map<Long, List<Map<String, Object>>> frameBeds = new LinkedHashMap<>();
        List<BedUnit> units = new ArrayList<>();
        for (Map<String, Object> bed : roomBeds) {
            Object frameValue = bed.get("bed_frame_id");
            if (frameValue == null) {
                units.add(loftUnit(bed));
                continue;
            }
            frameBeds.computeIfAbsent(number(frameValue), ignored -> new ArrayList<>()).add(bed);
        }
        for (List<Map<String, Object>> beds : frameBeds.values()) {
            beds.sort(Comparator.comparingInt(
                    bed -> ((Number) bed.get("position_index")).intValue()));
            if (!isGenuineBunkPair(beds)) {
                beds.stream().map(this::loftUnit).forEach(units::add);
                continue;
            }
            Map<String, Object> representative = beds.stream()
                    .filter(bed -> "BUNK_UPPER".equals(bed.get("bed_type")))
                    .findFirst()
                    .orElseThrow();
            units.add(new BedUnit(
                    number(representative.get("id")),
                    "BUNK",
                    List.copyOf(beds),
                    beds.stream().anyMatch(this::occupied)));
        }
        units.sort(Comparator.comparingLong(BedUnit::representativeBedId));
        return units;
    }

    private Set<Long> genuineBunkFrameIds(List<Map<String, Object>> roomBeds) {
        Map<Long, List<Map<String, Object>>> frameBeds = new LinkedHashMap<>();
        for (Map<String, Object> bed : roomBeds) {
            Object frameValue = bed.get("bed_frame_id");
            if (frameValue != null) {
                frameBeds.computeIfAbsent(number(frameValue), ignored -> new ArrayList<>())
                        .add(bed);
            }
        }
        Set<Long> result = new HashSet<>();
        frameBeds.forEach((frameId, beds) -> {
            if (isGenuineBunkPair(beds)) {
                result.add(frameId);
            }
        });
        return result;
    }

    private boolean isGenuineBunkPair(List<Map<String, Object>> beds) {
        if (beds.size() != 2) {
            return false;
        }
        Set<String> types = new HashSet<>();
        for (Map<String, Object> bed : beds) {
            types.add(String.valueOf(bed.get("bed_type")));
        }
        return types.equals(Set.of("BUNK_UPPER", "BUNK_LOWER"));
    }

    private BedUnit loftUnit(Map<String, Object> bed) {
        return new BedUnit(
                number(bed.get("id")),
                "LOFT_BED_DESK",
                List.of(bed),
                occupied(bed));
    }

    private void validateUnitSet(List<BedUnit> units, List<LayoutItem> items) {
        if (items == null || items.size() != units.size()) {
            throw new BusinessException(
                    "ROOM_LAYOUT_BED_MISMATCH",
                    "必须一次提交房间内全部床具单元的布局");
        }
        Set<Long> expected = new HashSet<>();
        for (BedUnit unit : units) {
            expected.add(unit.representativeBedId());
        }
        Set<Long> actual = new HashSet<>();
        for (LayoutItem item : items) {
            if (!actual.add(item.bedId())) {
                throw new BusinessException("ROOM_LAYOUT_BED_MISMATCH", "床具布局中存在重复项目");
            }
        }
        if (!expected.equals(actual)) {
            throw new BusinessException(
                    "ROOM_LAYOUT_BED_MISMATCH",
                    "布局包含缺失床具或其他房间的床具");
        }
    }

    private void validateItems(List<LayoutItem> items) {
        for (LayoutItem item : items) {
            if (!UNIT_TYPES.contains(item.bedType())) {
                throw new BusinessException(
                        "BED_TYPE_INVALID",
                        "床具类型只能为上床下桌或上下铺");
            }
            if (!Double.isFinite(item.layoutX()) || !Double.isFinite(item.layoutZ())
                    || item.layoutX() < MIN_X || item.layoutX() > MAX_X
                    || item.layoutZ() < MIN_Z || item.layoutZ() > MAX_Z) {
                throw new BusinessException(
                        "ROOM_LAYOUT_OUT_OF_BOUNDS",
                        "床具坐标超出房间可视区域");
            }
            if (!ROTATIONS.contains(item.rotationDegrees())) {
                throw new BusinessException(
                        "ROOM_LAYOUT_ROTATION_INVALID",
                        "床具朝向只能为0、90、180或270度");
            }
        }
    }

    private void splitLoftIntoBunk(
            long roomId,
            BedUnit unit,
            LayoutItem item,
            long operatorUserId) {
        Map<String, Object> source = unit.beds().getFirst();
        long sourceBedId = number(source.get("id"));
        int nextPosition = nextPosition(roomId);
        String frameCode = uniqueFrameCode(roomId, sourceBedId);

        GeneratedKeyHolder frameKey = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO bed_frame (room_id, frame_code, frame_type, enabled)
                VALUES (:roomId, :frameCode, 'BUNK_FRAME', 1)
                """, new MapSqlParameterSource()
                .addValue("roomId", roomId)
                .addValue("frameCode", frameCode),
                frameKey,
                new String[]{"id"});
        long frameId = frameKey.getKey().longValue();

        jdbc.update("""
                UPDATE bed
                SET bed_frame_id=:frameId, bed_type='BUNK_UPPER', version=version+1
                WHERE id=:bedId
                """, new MapSqlParameterSource()
                .addValue("frameId", frameId)
                .addValue("bedId", sourceBedId));

        GeneratedKeyHolder lowerKey = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO bed
                (room_id, bed_frame_id, bed_code, bed_type, position_index, operational_status)
                VALUES (:roomId, :frameId, :bedCode, 'BUNK_LOWER', :positionIndex, 'ENABLED')
                """, new MapSqlParameterSource()
                .addValue("roomId", roomId)
                .addValue("frameId", frameId)
                .addValue("bedCode", uniqueBedCode(roomId, nextPosition))
                .addValue("positionIndex", nextPosition),
                lowerKey,
                new String[]{"id"});
        long lowerBedId = lowerKey.getKey().longValue();

        jdbc.update("""
                INSERT IGNORE INTO batch_bed_scope (batch_id, bed_id)
                SELECT batch_id, :newBedId
                FROM batch_bed_scope
                WHERE bed_id=:sourceBedId
                """, new MapSqlParameterSource()
                .addValue("newBedId", lowerBedId)
                .addValue("sourceBedId", sourceBedId));

        savePlacement(
                List.of(Map.of("id", sourceBedId), Map.of("id", lowerBedId)),
                item,
                operatorUserId);
    }

    private void savePlacement(
            List<Map<String, Object>> beds,
            LayoutItem item,
            long operatorUserId) {
        for (Map<String, Object> bed : beds) {
            jdbc.update("""
                    INSERT INTO room_bed_layout
                    (bed_id, layout_x, layout_z, rotation_degrees, updated_by)
                    VALUES (:bedId, :layoutX, :layoutZ, :rotation, :updatedBy)
                    ON DUPLICATE KEY UPDATE
                        layout_x=VALUES(layout_x),
                        layout_z=VALUES(layout_z),
                        rotation_degrees=VALUES(rotation_degrees),
                        updated_by=VALUES(updated_by),
                        version=version+1
                    """, new MapSqlParameterSource()
                    .addValue("bedId", number(bed.get("id")))
                    .addValue("layoutX", item.layoutX())
                    .addValue("layoutZ", item.layoutZ())
                    .addValue("rotation", item.rotationDegrees())
                    .addValue("updatedBy", operatorUserId));
        }
    }

    private int nextPosition(long roomId) {
        Integer value = jdbc.queryForObject("""
                SELECT COALESCE(MAX(position_index), 0) + 1
                FROM bed WHERE room_id=:roomId
                """, Map.of("roomId", roomId), Integer.class);
        return value == null ? 1 : value;
    }

    private String uniqueFrameCode(long roomId, long bedId) {
        String base = "BF-" + roomId + "-" + bedId;
        if (base.length() <= 32) {
            return base;
        }
        return "BF-" + bedId;
    }

    private String uniqueBedCode(long roomId, int positionIndex) {
        String candidate = "B" + positionIndex;
        int suffix = positionIndex;
        while (count("""
                SELECT COUNT(*) FROM bed
                WHERE room_id=:roomId AND bed_code=:bedCode
                """, new MapSqlParameterSource()
                .addValue("roomId", roomId)
                .addValue("bedCode", candidate)) > 0) {
            suffix++;
            candidate = "B" + suffix;
        }
        return candidate;
    }

    private boolean occupied(Map<String, Object> bed) {
        return ((Number) bed.get("occupied")).intValue() == 1;
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private int count(String sql, MapSqlParameterSource parameters) {
        Integer value = jdbc.queryForObject(sql, parameters, Integer.class);
        return value == null ? 0 : value;
    }

    private String roomTypeForBedCount(int bedCount) {
        return switch (bedCount) {
            case 4 -> "FOUR_PERSON";
            case 5 -> "FIVE_PERSON";
            case 6 -> "SIX_PERSON";
            default -> "OTHER";
        };
    }

    private DefaultPlacement defaultPlacement(String bedType, int positionIndex) {
        if ("BUNK_UPPER".equals(bedType) || "BUNK_LOWER".equals(bedType)) {
            return new DefaultPlacement(2.35, 1.65, 0);
        }
        return switch (positionIndex) {
            case 1 -> new DefaultPlacement(-2.35, -1.65, 0);
            case 2 -> new DefaultPlacement(2.35, -1.65, 0);
            case 3 -> new DefaultPlacement(-2.35, 1.65, 0);
            default -> new DefaultPlacement(2.35, 1.65, 0);
        };
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

    public record LayoutCommand(
            long expectedRoomVersion,
            String reason,
            List<LayoutItem> beds) {
    }

    public record LayoutItem(
            long bedId,
            String bedType,
            double layoutX,
            double layoutZ,
            int rotationDegrees) {
    }

    private record BedUnit(
            long representativeBedId,
            String unitType,
            List<Map<String, Object>> beds,
            boolean occupied) {
    }

    private record DefaultPlacement(double x, double z, int rotationDegrees) {
    }
}
