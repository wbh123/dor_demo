package com.wust.dormitory.admin;

import com.wust.dormitory.admin.mapper.RoomCatalogMapper;
import com.wust.dormitory.admin.mapper.RoomManagementMapper;
import com.wust.dormitory.admin.model.persistence.RoomCatalogRow;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

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

    private final RoomManagementMapper managementMapper;
    private final AuditService auditService;
    private final RoomCatalogMapper roomCatalogMapper;

    public RoomManagementService(
            RoomManagementMapper managementMapper,
            AuditService auditService,
            RoomCatalogMapper roomCatalogMapper) {
        this.managementMapper = managementMapper;
        this.auditService = auditService;
        this.roomCatalogMapper = roomCatalogMapper;
    }

    public List<Map<String, Object>> buildings() {
        return managementMapper.findBuildings();
    }

    public List<Map<String, Object>> rooms(Long buildingId, String gender) {
        return roomCatalogMapper.findRooms(buildingId, gender).stream()
                .map(RoomCatalogRow::asResponseMap)
                .toList();
    }

    @Transactional
    public long createBuilding(BuildingCommand command, CurrentUser operator) {
        validateBuildingCommand(command);
        Long campusId = managementMapper.findDefaultCampusId();
        if (campusId == null) {
            throw new BusinessException("CAMPUS_REQUIRED", "请先配置可用校区", HttpStatus.NOT_FOUND);
        }
        if (managementMapper.countBuildingByCode(command.buildingCode()) > 0) {
            throw new BusinessException("BUILDING_CODE_DUPLICATE", "宿舍楼代码已存在", HttpStatus.CONFLICT);
        }
        Map<String, Object> building = new LinkedHashMap<>();
        building.put("campusId", campusId);
        building.put("code", command.buildingCode().trim());
        building.put("name", command.buildingName().trim());
        building.put("gender", command.gender());
        building.put("educationScope", command.educationLevelScope());
        building.put("residentScope", command.residentScope());
        managementMapper.insertBuilding(building);
        long buildingId = number(building, "id");
        managementMapper.batchInsertFloors(
                buildingId,
                IntStream.rangeClosed(1, command.floorCount()).boxed().toList());
        auditService.success(operator, "BUILDING_CREATE", "BUILDING", buildingId,
                command.reason().trim(), null, command.asAuditMap());
        return buildingId;
    }

    @Transactional
    public long createRoom(RoomCreateCommand command, CurrentUser operator) {
        validateRoomCommand(command.gender(), command.educationLevelScope(),
                command.residentScope(), command.operationalStatus());
        Map<String, Object> building = buildingForValidation(command.buildingId());
        validateRoomWithinBuilding(building, command.gender(), command.educationLevelScope(), command.residentScope());
        Long floorId = managementMapper.findFloorId(command.buildingId(), command.floorNumber());
        if (floorId == null) {
            throw new BusinessException(
                    "FLOOR_NOT_FOUND", "所选楼层不存在，请先确认宿舍楼楼层设置", HttpStatus.NOT_FOUND);
        }
        String roomNumber = command.roomNumber().trim();
        if (managementMapper.countRoomNumber(floorId, roomNumber) > 0) {
            throw new BusinessException("ROOM_NUMBER_DUPLICATE", "该楼层已存在相同宿舍号", HttpStatus.CONFLICT);
        }
        Map<String, Object> room = new LinkedHashMap<>();
        room.put("floorId", floorId);
        room.put("roomNumber", roomNumber);
        room.put("roomType", roomTypeForBedCount(command.capacity()));
        room.put("capacity", command.capacity());
        room.put("gender", command.gender());
        room.put("educationScope", command.educationLevelScope());
        room.put("residentScope", command.residentScope());
        room.put("status", command.operationalStatus());
        room.put("remark", normalizeNullable(command.remark()));
        managementMapper.insertRoom(room);
        long roomId = number(room, "id");
        managementMapper.batchInsertBeds(
                roomId,
                IntStream.rangeClosed(1, command.capacity()).boxed().toList());
        auditService.success(operator, "ROOM_CREATE", "ROOM", roomId,
                command.reason().trim(), null, command.asAuditMap());
        return roomId;
    }

    @Transactional
    public void updateRoom(long roomId, RoomCommand command, CurrentUser operator) {
        Map<String, Object> before = emptyIfNull(managementMapper.lockRoomForUpdate(roomId));
        if (before.isEmpty()) {
            throw new BusinessException("ROOM_NOT_FOUND", "房间不存在", HttpStatus.NOT_FOUND);
        }
        int physicalBedCount = managementMapper.countPhysicalBeds(roomId);
        if (physicalBedCount < 1 || command.capacity() != physicalBedCount) {
            throw new BusinessException("ROOM_CAPACITY_MISMATCH", "房间容量必须等于当前床位总数");
        }
        validateRoomCommand(command.gender(), command.educationLevelScope(),
                command.residentScope(), command.operationalStatus());
        validateRoomWithinBuilding(before, command.gender(), command.educationLevelScope(), command.residentScope());
        validateActiveResidents(roomId, command);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("id", roomId);
        update.put("capacity", physicalBedCount);
        update.put("gender", command.gender());
        update.put("educationScope", command.educationLevelScope());
        update.put("residentScope", command.residentScope());
        update.put("status", command.operationalStatus());
        update.put("remark", normalizeNullable(command.remark()));
        managementMapper.updateRoom(update);

        Map<String, Object> after = new LinkedHashMap<>(command.asAuditMap());
        after.put("roomType", before.get("room_type"));
        after.put("capacity", physicalBedCount);
        auditService.success(operator, "ROOM_UPDATE", "ROOM", roomId,
                command.reason().trim(), before, after);
    }

    private void validateActiveResidents(long roomId, RoomCommand command) {
        int incompatibleResidents = managementMapper.countIncompatibleResidents(
                roomId, command.gender(), command.residentScope(), command.educationLevelScope());
        if (incompatibleResidents > 0) {
            throw new BusinessException(
                    "ROOM_RESIDENT_SCOPE_CONFLICT",
                    "当前已有在住学生与新的房间属性不一致，请先办理换寝或退宿",
                    HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> buildingForValidation(long buildingId) {
        Map<String, Object> building = emptyIfNull(managementMapper.findBuildingForValidation(buildingId));
        if (building.isEmpty()) {
            throw new BusinessException("BUILDING_NOT_FOUND", "宿舍楼不存在或已停用", HttpStatus.NOT_FOUND);
        }
        return building;
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

    private void validateRoomCommand(String gender, String educationScope, String residentScope, String status) {
        if (!ROOM_GENDERS.contains(gender)
                || !EDUCATION_SCOPES.contains(educationScope)
                || !RESIDENT_SCOPES.contains(residentScope)
                || !OPERATIONAL_STATUSES.contains(status)) {
            throw new BusinessException("ROOM_SCOPE_INVALID", "请选择有效的房间属性");
        }
    }

    private Map<String, Object> emptyIfNull(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
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
