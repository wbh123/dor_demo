package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RoomLayoutPlanner {
    public static final int MAX_ROOM_CAPACITY = 8;
    private static final double MIN_X = -5.2;
    private static final double MAX_X = 5.2;
    private static final double MIN_Z = -3.5;
    private static final double MAX_Z = 3.5;
    private static final Set<Integer> ROTATIONS = Set.of(0, 90, 180, 270);
    private static final Set<String> UNIT_TYPES = Set.of("LOFT_BED_DESK", "BUNK", "SINGLE_BED");

    public List<BedUnit> buildUnits(List<Map<String, Object>> roomBeds) {
        Map<Long, List<Map<String, Object>>> frameBeds = new LinkedHashMap<>();
        List<BedUnit> units = new ArrayList<>();
        for (Map<String, Object> bed : roomBeds) {
            Object frameValue = bed.get("bed_frame_id");
            if (frameValue == null) {
                units.add(independentUnit(bed));
            } else {
                frameBeds.computeIfAbsent(number(frameValue), ignored -> new ArrayList<>()).add(bed);
            }
        }
        for (List<Map<String, Object>> beds : frameBeds.values()) {
            beds.sort(Comparator.comparingInt(bed -> ((Number) bed.get("position_index")).intValue()));
            if (!isGenuineBunkPair(beds)) {
                beds.stream().map(this::independentUnit).forEach(units::add);
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

    public Set<Long> genuineBunkFrameIds(List<Map<String, Object>> roomBeds) {
        Map<Long, List<Map<String, Object>>> frameBeds = new LinkedHashMap<>();
        for (Map<String, Object> bed : roomBeds) {
            Object frameValue = bed.get("bed_frame_id");
            if (frameValue != null) {
                frameBeds.computeIfAbsent(number(frameValue), ignored -> new ArrayList<>()).add(bed);
            }
        }
        Set<Long> result = new HashSet<>();
        frameBeds.forEach((frameId, beds) -> {
            if (isGenuineBunkPair(beds)) result.add(frameId);
        });
        return result;
    }

    public void validateUnitSet(List<BedUnit> units, List<RoomLayoutService.LayoutItem> items) {
        if (items == null || items.size() != units.size()) {
            throw new BusinessException("ROOM_LAYOUT_BED_MISMATCH", "必须一次提交房间内全部床具单元的布局");
        }
        Set<Long> expected = new HashSet<>();
        units.forEach(unit -> expected.add(unit.representativeBedId()));
        Set<Long> actual = new HashSet<>();
        for (RoomLayoutService.LayoutItem item : items) {
            if (!actual.add(item.bedId())) {
                throw new BusinessException("ROOM_LAYOUT_BED_MISMATCH", "床具布局中存在重复项目");
            }
        }
        if (!expected.equals(actual)) {
            throw new BusinessException("ROOM_LAYOUT_BED_MISMATCH", "布局包含缺失床具或其他房间的床具");
        }
    }

    public void validateItems(List<RoomLayoutService.LayoutItem> items) {
        for (RoomLayoutService.LayoutItem item : items) {
            if (!UNIT_TYPES.contains(item.bedType())) {
                throw new BusinessException("BED_TYPE_INVALID", "床具类型只能为上床下桌、上下铺或单人床");
            }
            if (!Double.isFinite(item.layoutX()) || !Double.isFinite(item.layoutZ())
                    || item.layoutX() < MIN_X || item.layoutX() > MAX_X
                    || item.layoutZ() < MIN_Z || item.layoutZ() > MAX_Z) {
                throw new BusinessException("ROOM_LAYOUT_OUT_OF_BOUNDS", "床具坐标超出房间可视区域");
            }
            if (!ROTATIONS.contains(item.rotationDegrees())) {
                throw new BusinessException("ROOM_LAYOUT_ROTATION_INVALID", "床具朝向只能为0、90、180或270度");
            }
        }
    }

    public String layoutUnitType(Map<String, Object> bed, Set<Long> genuineBunkFrames) {
        Object frameValue = bed.get("bed_frame_id");
        if (frameValue != null && genuineBunkFrames.contains(number(frameValue))) return "BUNK";
        return "SINGLE_BED".equals(String.valueOf(bed.get("bed_type"))) ? "SINGLE_BED" : "LOFT_BED_DESK";
    }

    public String roomTypeForBedCount(int bedCount) {
        return switch (bedCount) {
            case 4 -> "FOUR_PERSON";
            case 5 -> "FIVE_PERSON";
            case 6 -> "SIX_PERSON";
            default -> "OTHER";
        };
    }

    public DefaultPlacement defaultPlacement(String bedType, int positionIndex) {
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

    public boolean occupied(Map<String, Object> bed) {
        return ((Number) bed.get("occupied")).intValue() == 1;
    }

    public long number(Object value) {
        return ((Number) value).longValue();
    }

    private boolean isGenuineBunkPair(List<Map<String, Object>> beds) {
        if (beds.size() != 2) return false;
        Set<String> types = new HashSet<>();
        beds.forEach(bed -> types.add(String.valueOf(bed.get("bed_type"))));
        return types.equals(Set.of("BUNK_UPPER", "BUNK_LOWER"));
    }

    private BedUnit independentUnit(Map<String, Object> bed) {
        String unitType = "SINGLE_BED".equals(String.valueOf(bed.get("bed_type")))
                ? "SINGLE_BED" : "LOFT_BED_DESK";
        return new BedUnit(number(bed.get("id")), unitType, List.of(bed), occupied(bed));
    }

    public record BedUnit(
            long representativeBedId,
            String unitType,
            List<Map<String, Object>> beds,
            boolean occupied) {
    }

    public record DefaultPlacement(double x, double z, int rotationDegrees) {
    }
}
