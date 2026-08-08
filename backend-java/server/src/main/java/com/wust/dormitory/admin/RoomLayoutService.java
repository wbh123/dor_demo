package com.wust.dormitory.admin;

import com.wust.dormitory.admin.RoomLayoutPlanner.DefaultPlacement;
import com.wust.dormitory.admin.mapper.RoomLayoutMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RoomLayoutService {
    public static final String DEFAULT_LAYOUT = "DEFAULT_LAYOUT";
    public static final String CUSTOM_LAYOUT = "CUSTOM_LAYOUT";
    private static final DefaultPlacement STANDARD_TOP_LEFT = new DefaultPlacement(-2.35, -1.65, 0);

    private final RoomLayoutMapper roomLayoutMapper;
    private final RoomLayoutPlanner planner;
    private final AuditService auditService;

    public RoomLayoutService(
            RoomLayoutMapper roomLayoutMapper,
            RoomLayoutPlanner planner,
            AuditService auditService) {
        this.roomLayoutMapper = roomLayoutMapper;
        this.planner = planner;
        this.auditService = auditService;
    }

    public Map<String, Object> getLayout(long roomId) {
        Map<String, Object> room = roomLayoutMapper.findRoomLayout(roomId);
        if (room == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "房间不存在", HttpStatus.NOT_FOUND);
        }
        List<Map<String, Object>> rows = roomLayoutMapper.findBeds(roomId);
        Set<Long> genuineBunkFrames = planner.genuineBunkFrameIds(rows);
        boolean hasCustom = false;
        List<Map<String, Object>> beds = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Map<String, Object> bed = new LinkedHashMap<>(row);
            boolean custom = ((Number) row.get("custom_layout")).intValue() == 1;
            hasCustom |= custom;
            if (!custom) {
                String bedType = String.valueOf(row.get("bed_type"));
                int position = ((Number) row.get("position_index")).intValue();
                DefaultPlacement placement = planner.defaultPlacement(bedType, position);
                if (position == 1 && "LOFT_BED_DESK".equals(bedType)
                        && !STANDARD_TOP_LEFT.equals(placement)) {
                    throw new IllegalStateException("标准2×2布局左上床具坐标发生漂移");
                }
                bed.put("layout_x", placement.x());
                bed.put("layout_z", placement.z());
                bed.put("rotation_degrees", placement.rotationDegrees());
            }
            bed.put("layout_unit_type", planner.layoutUnitType(row, genuineBunkFrames));
            beds.add(bed);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("room", room);
        result.put("beds", beds);
        result.put("layout_source", hasCustom ? CUSTOM_LAYOUT : DEFAULT_LAYOUT);
        result.put("maximum_capacity", RoomLayoutPlanner.MAX_ROOM_CAPACITY);
        return result;
    }

    @Transactional
    public Map<String, Object> updateLayout(long roomId, LayoutCommand command, CurrentUser operator) {
        Map<String, Object> room = roomLayoutMapper.lockRoom(roomId);
        if (room == null) {
            throw new BusinessException("ROOM_NOT_FOUND", "房间不存在", HttpStatus.NOT_FOUND);
        }
        long currentVersion = ((Number) room.get("version")).longValue();
        if (currentVersion != command.expectedRoomVersion()) throw versionConflict();
        if (command.reason() == null || command.reason().isBlank()) {
            throw new BusinessException("ROOM_LAYOUT_REASON_REQUIRED", "请填写布局修改原因");
        }

        List<Map<String, Object>> roomBeds = roomLayoutMapper.lockRoomBeds(roomId);
        List<RoomLayoutPlanner.BedUnit> units = planner.buildUnits(roomBeds);
        planner.validateUnitSet(units, command.beds());
        planner.validateItems(command.beds());
        Map<String, Object> before = getLayout(roomId);
        Map<Long, LayoutItem> itemById = new HashMap<>();
        command.beds().forEach(item -> itemById.put(item.bedId(), item));

        int capacity = roomBeds.size();
        for (RoomLayoutPlanner.BedUnit unit : units) {
            LayoutItem item = itemById.get(unit.representativeBedId());
            if (unit.occupied() && !unit.unitType().equals(item.bedType())) {
                throw new BusinessException("BED_TYPE_OCCUPIED", "非空床位不能修改床位类型", HttpStatus.CONFLICT);
            }
            if ("BUNK".equals(unit.unitType()) && !"BUNK".equals(item.bedType())) {
                collapseBunkUnit(roomId, unit, item, operator.userId());
                capacity--;
            } else if (!"BUNK".equals(unit.unitType()) && "BUNK".equals(item.bedType())) {
                if (capacity >= RoomLayoutPlanner.MAX_ROOM_CAPACITY) {
                    throw new BusinessException("ROOM_CAPACITY_LIMIT", "房间最多只能配置8个床位", HttpStatus.CONFLICT);
                }
                splitIntoBunk(roomId, unit, item, operator.userId());
                capacity++;
            } else {
                updateIndependentType(unit, item);
                savePlacement(unit.beds(), item, operator.userId());
            }
        }

        int updated = roomLayoutMapper.updateRoomVersioned(
                roomId, planner.roomTypeForBedCount(capacity), capacity, command.expectedRoomVersion());
        if (updated != 1) throw versionConflict();
        Map<String, Object> after = getLayout(roomId);
        auditService.success(operator, "ROOM_LAYOUT_UPDATE", "ROOM", roomId,
                command.reason().trim(), before, after);
        return after;
    }

    private void updateIndependentType(RoomLayoutPlanner.BedUnit unit, LayoutItem item) {
        if (!"BUNK".equals(unit.unitType()) && !unit.unitType().equals(item.bedType())) {
            roomLayoutMapper.updateIndependentBedType(unit.representativeBedId(), item.bedType());
        }
    }

    private void collapseBunkUnit(
            long roomId, RoomLayoutPlanner.BedUnit unit, LayoutItem item, long operatorUserId) {
        if (unit.occupied()) {
            throw new BusinessException("BED_TYPE_OCCUPIED", "上下铺有人在住或已分配时不能合并床型", HttpStatus.CONFLICT);
        }
        Map<String, Object> representative = unit.beds().stream()
                .filter(bed -> planner.number(bed.get("id")) == unit.representativeBedId())
                .findFirst().orElseThrow();
        Map<String, Object> removed = unit.beds().stream()
                .filter(bed -> planner.number(bed.get("id")) != unit.representativeBedId())
                .findFirst().orElseThrow();
        long removedBedId = planner.number(removed.get("id"));
        Object frameValue = representative.get("bed_frame_id");
        roomLayoutMapper.deleteBedScope(removedBedId);
        roomLayoutMapper.deletePlacement(removedBedId);
        if (roomLayoutMapper.retireBed(removedBedId, roomId) != 1) {
            throw new BusinessException("BUNK_COLLAPSE_CONFLICT", "上下铺床位状态已变化，请重新加载后再试", HttpStatus.CONFLICT);
        }
        roomLayoutMapper.updateRepresentativeAfterCollapse(unit.representativeBedId(), roomId, item.bedType());
        if (frameValue != null) roomLayoutMapper.deleteFrame(planner.number(frameValue), roomId);
        savePlacement(List.of(Map.of("id", unit.representativeBedId())), item, operatorUserId);
    }

    private void splitIntoBunk(
            long roomId, RoomLayoutPlanner.BedUnit unit, LayoutItem item, long operatorUserId) {
        long sourceBedId = planner.number(unit.beds().getFirst().get("id"));
        int nextPosition = roomLayoutMapper.nextPosition(roomId);
        Map<String, Object> frame = new HashMap<>();
        frame.put("roomId", roomId);
        frame.put("frameCode", uniqueFrameCode(roomId, sourceBedId));
        roomLayoutMapper.insertFrame(frame);
        long frameId = generatedId(frame, "床架");
        roomLayoutMapper.updateSourceToUpper(sourceBedId, frameId);

        Map<String, Object> lower = new HashMap<>();
        lower.put("roomId", roomId);
        lower.put("frameId", frameId);
        lower.put("bedCode", uniqueBedCode(roomId, nextPosition));
        lower.put("positionIndex", nextPosition);
        roomLayoutMapper.insertLowerBed(lower);
        long lowerBedId = generatedId(lower, "下铺床位");
        roomLayoutMapper.copyBedScope(lowerBedId, sourceBedId);
        savePlacement(List.of(Map.of("id", sourceBedId), Map.of("id", lowerBedId)), item, operatorUserId);
    }

    private void savePlacement(List<Map<String, Object>> beds, LayoutItem item, long operatorUserId) {
        List<Map<String, Object>> values = beds.stream().map(bed -> Map.<String, Object>of(
                "bedId", planner.number(bed.get("id")),
                "layoutX", item.layoutX(),
                "layoutZ", item.layoutZ(),
                "rotation", item.rotationDegrees(),
                "updatedBy", operatorUserId)).toList();
        roomLayoutMapper.batchUpsertPlacements(values);
    }

    private String uniqueBedCode(long roomId, int positionIndex) {
        int suffix = positionIndex;
        String candidate = "B" + suffix;
        while (roomLayoutMapper.countBedCode(roomId, candidate) > 0) candidate = "B" + (++suffix);
        return candidate;
    }

    private String uniqueFrameCode(long roomId, long bedId) {
        String base = "BF-" + roomId + "-" + bedId;
        return base.length() <= 32 ? base : "BF-" + bedId;
    }

    private long generatedId(Map<String, Object> values, String entity) {
        Object key = values.get("id");
        if (key instanceof Number number) return number.longValue();
        throw new IllegalStateException(entity + "创建成功但未返回编号");
    }

    private BusinessException versionConflict() {
        return new BusinessException("ROOM_LAYOUT_VERSION_CONFLICT",
                "房间布局已被其他管理员修改，请重新加载后再保存", HttpStatus.CONFLICT);
    }

    public record LayoutCommand(long expectedRoomVersion, String reason, List<LayoutItem> beds) {
    }

    public record LayoutItem(long bedId, String bedType, double layoutX, double layoutZ, int rotationDegrees) {
    }
}
