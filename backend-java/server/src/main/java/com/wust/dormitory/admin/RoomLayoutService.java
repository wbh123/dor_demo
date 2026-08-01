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

    private static final double MIN_X = -5.2;
    private static final double MAX_X = 5.2;
    private static final double MIN_Z = -3.5;
    private static final double MAX_Z = 3.5;
    private static final Set<Integer> ROTATIONS = Set.of(0, 90, 180, 270);

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
                       layout.layout_x, layout.layout_z, layout.rotation_degrees,
                       CASE WHEN layout.bed_id IS NULL THEN 0 ELSE 1 END AS custom_layout
                FROM bed
                LEFT JOIN room_bed_layout layout ON layout.bed_id=bed.id
                WHERE bed.room_id=:roomId
                ORDER BY bed.position_index, bed.id
                """, Map.of("roomId", roomId));

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
            beds.add(bed);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("room", room);
        result.put("beds", beds);
        result.put("layout_source", hasCustom ? CUSTOM_LAYOUT : DEFAULT_LAYOUT);
        return result;
    }

    @Transactional
    public Map<String, Object> updateLayout(
            long roomId,
            LayoutCommand command,
            CurrentUser operator) {
        Map<String, Object> room = one("""
                SELECT id, version, state_version
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

        List<Map<String, Object>> roomBeds = jdbc.queryForList("""
                SELECT id, bed_code, bed_type, position_index, bed_frame_id
                FROM bed
                WHERE room_id=:roomId
                ORDER BY position_index, id
                """, Map.of("roomId", roomId));
        validateBedSet(roomBeds, command.beds());
        validateItems(command.beds());
        validateBunkFrames(roomBeds, command.beds());

        Map<String, Object> before = getLayout(roomId);
        for (LayoutItem item : command.beds()) {
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
                    .addValue("bedId", item.bedId())
                    .addValue("layoutX", item.layoutX())
                    .addValue("layoutZ", item.layoutZ())
                    .addValue("rotation", item.rotationDegrees())
                    .addValue("updatedBy", operator.userId()));
        }

        int updated = jdbc.update("""
                UPDATE room
                SET version=version+1, state_version=state_version+1
                WHERE id=:roomId AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("roomId", roomId)
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

    private void validateBedSet(List<Map<String, Object>> roomBeds, List<LayoutItem> items) {
        if (items == null || items.size() != roomBeds.size()) {
            throw new BusinessException(
                    "ROOM_LAYOUT_BED_MISMATCH",
                    "必须一次提交房间内全部床位的布局");
        }
        Set<Long> expected = new HashSet<>();
        for (Map<String, Object> bed : roomBeds) {
            expected.add(((Number) bed.get("id")).longValue());
        }
        Set<Long> actual = new HashSet<>();
        for (LayoutItem item : items) {
            if (!actual.add(item.bedId())) {
                throw new BusinessException("ROOM_LAYOUT_BED_MISMATCH", "床位布局中存在重复床位");
            }
        }
        if (!expected.equals(actual)) {
            throw new BusinessException(
                    "ROOM_LAYOUT_BED_MISMATCH",
                    "布局包含缺失床位或其他房间的床位");
        }
    }

    private void validateItems(List<LayoutItem> items) {
        for (LayoutItem item : items) {
            if (!Double.isFinite(item.layoutX()) || !Double.isFinite(item.layoutZ())
                    || item.layoutX() < MIN_X || item.layoutX() > MAX_X
                    || item.layoutZ() < MIN_Z || item.layoutZ() > MAX_Z) {
                throw new BusinessException(
                        "ROOM_LAYOUT_OUT_OF_BOUNDS",
                        "床位坐标超出房间可视区域");
            }
            if (!ROTATIONS.contains(item.rotationDegrees())) {
                throw new BusinessException(
                        "ROOM_LAYOUT_ROTATION_INVALID",
                        "床位朝向只能为0、90、180或270度");
            }
        }
    }

    private void validateBunkFrames(
            List<Map<String, Object>> roomBeds,
            List<LayoutItem> items) {
        Map<Long, LayoutItem> itemByBed = new HashMap<>();
        for (LayoutItem item : items) {
            itemByBed.put(item.bedId(), item);
        }

        Map<Long, LayoutItem> frameAnchor = new HashMap<>();
        for (Map<String, Object> bed : roomBeds) {
            Object frameValue = bed.get("bed_frame_id");
            String type = String.valueOf(bed.get("bed_type"));
            if (frameValue == null || !(type.equals("BUNK_UPPER") || type.equals("BUNK_LOWER"))) {
                continue;
            }
            long frameId = ((Number) frameValue).longValue();
            long bedId = ((Number) bed.get("id")).longValue();
            LayoutItem item = itemByBed.get(bedId);
            LayoutItem anchor = frameAnchor.putIfAbsent(frameId, item);
            if (anchor != null && !samePlacement(anchor, item)) {
                throw new BusinessException(
                        "ROOM_LAYOUT_BUNK_MISMATCH",
                        "同一上下铺床架的上下层必须共享位置和朝向");
            }
        }
    }

    private boolean samePlacement(LayoutItem left, LayoutItem right) {
        return Math.abs(left.layoutX() - right.layoutX()) < 0.0001
                && Math.abs(left.layoutZ() - right.layoutZ()) < 0.0001
                && left.rotationDegrees() == right.rotationDegrees();
    }

    private DefaultPlacement defaultPlacement(String bedType, int positionIndex) {
        if ("BUNK_UPPER".equals(bedType) || "BUNK_LOWER".equals(bedType)) {
            return new DefaultPlacement(3.35, 1.5, 90);
        }
        return switch (positionIndex) {
            case 1 -> new DefaultPlacement(-2.85, -1.45, 90);
            case 2 -> new DefaultPlacement(-2.85, 1.5, 90);
            case 3 -> new DefaultPlacement(0.15, 1.5, 90);
            default -> new DefaultPlacement(0.15, -1.45, 90);
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
            double layoutX,
            double layoutZ,
            int rotationDegrees) {
    }

    private record DefaultPlacement(double x, double z, int rotationDegrees) {
    }
}
