package com.wust.dormitory.admin;

import com.wust.dormitory.admin.mapper.BuildingManagementMapper;
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

@Service
public class BuildingManagementService {
    private static final Set<String> GENDERS = Set.of("M", "F", "MIXED");
    private static final Set<String> EDUCATION_SCOPES = Set.of(
            "UNDERGRADUATE_ONLY", "GRADUATE_ONLY", "MIXED");
    private static final Set<String> RESIDENT_SCOPES = Set.of(
            "DOMESTIC_ONLY", "INTERNATIONAL_ONLY", "MIXED");

    private final BuildingManagementMapper mapper;
    private final AuditService auditService;
    private final ReferenceDataCacheService referenceDataCacheService;

    public BuildingManagementService(
            BuildingManagementMapper mapper,
            AuditService auditService,
            ReferenceDataCacheService referenceDataCacheService) {
        this.mapper = mapper;
        this.auditService = auditService;
        this.referenceDataCacheService = referenceDataCacheService;
    }

    public List<Map<String, Object>> list() {
        return mapper.listDetails();
    }

    @Transactional
    public Map<String, Object> update(
            long buildingId,
            BuildingUpdateCommand command,
            CurrentUser operator) {
        validate(command);
        Map<String, Object> before = mapper.findForUpdate(buildingId);
        if (before == null || before.isEmpty()) {
            throw new BusinessException(
                    "BUILDING_NOT_FOUND",
                    "宿舍楼不存在",
                    HttpStatus.NOT_FOUND);
        }
        if (mapper.countDuplicateCode(buildingId, command.buildingCode().trim()) > 0) {
            throw new BusinessException(
                    "BUILDING_CODE_DUPLICATE",
                    "宿舍楼代码已存在",
                    HttpStatus.CONFLICT);
        }
        if (scopeChanged(before, command)
                && mapper.countIncompatibleRooms(
                        buildingId,
                        command.gender(),
                        command.educationLevelScope(),
                        command.residentScope()) > 0) {
            throw new BusinessException(
                    "BUILDING_SCOPE_CONFLICT",
                    "新的楼栋适用范围与现有寝室不一致，请先调整相关寝室；仅修改名称、代码、状态或楼层时不会触发该限制",
                    HttpStatus.CONFLICT);
        }
        if (mapper.countRoomsAboveFloor(buildingId, command.floorCount()) > 0) {
            throw new BusinessException(
                    "BUILDING_FLOOR_CONFLICT",
                    "拟移除的高楼层仍有寝室，不能减少楼层数",
                    HttpStatus.CONFLICT);
        }

        int updated = mapper.updateBuilding(
                buildingId,
                command.buildingCode().trim(),
                command.buildingName().trim(),
                command.gender(),
                command.educationLevelScope(),
                command.residentScope(),
                command.enabled());
        if (updated != 1) {
            throw new BusinessException(
                    "BUILDING_UPDATE_CONFLICT",
                    "宿舍楼状态已变化，请重新加载后再试",
                    HttpStatus.CONFLICT);
        }

        for (int floor = 1; floor <= command.floorCount(); floor++) {
            mapper.insertFloorIfMissing(buildingId, floor, floor + "层");
        }
        mapper.enableFloorsWithinRange(buildingId, command.floorCount());
        mapper.disableFloorsAboveRange(buildingId, command.floorCount());
        referenceDataCacheService.invalidateBuilding(buildingId);

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("buildingCode", command.buildingCode().trim());
        after.put("buildingName", command.buildingName().trim());
        after.put("gender", command.gender());
        after.put("educationLevelScope", command.educationLevelScope());
        after.put("residentScope", command.residentScope());
        after.put("floorCount", command.floorCount());
        after.put("enabled", command.enabled());
        auditService.success(
                operator,
                "BUILDING_UPDATE",
                "BUILDING",
                buildingId,
                command.reason().trim(),
                before,
                after);
        return after;
    }

    private boolean scopeChanged(
            Map<String, Object> before,
            BuildingUpdateCommand command) {
        return !command.gender().equals(String.valueOf(before.get("gender_restriction")))
                || !command.educationLevelScope().equals(String.valueOf(before.get("education_level_scope")))
                || !command.residentScope().equals(String.valueOf(before.get("resident_scope")));
    }

    private void validate(BuildingUpdateCommand command) {
        if (command.buildingCode() == null || command.buildingCode().isBlank()
                || command.buildingName() == null || command.buildingName().isBlank()) {
            throw new BusinessException("BUILDING_NAME_REQUIRED", "请填写楼栋代码和名称");
        }
        if (!GENDERS.contains(command.gender())
                || !EDUCATION_SCOPES.contains(command.educationLevelScope())
                || !RESIDENT_SCOPES.contains(command.residentScope())) {
            throw new BusinessException("BUILDING_SCOPE_INVALID", "请选择有效的楼栋适用范围");
        }
        if (command.floorCount() < 1 || command.floorCount() > 50) {
            throw new BusinessException("BUILDING_FLOOR_COUNT_INVALID", "宿舍楼层数应为1至50层");
        }
        if (command.reason() == null || command.reason().trim().length() < 2) {
            throw new BusinessException("BUILDING_REASON_REQUIRED", "修改原因至少填写2个字符");
        }
    }

    public record BuildingUpdateCommand(
            String buildingCode,
            String buildingName,
            String gender,
            String educationLevelScope,
            String residentScope,
            int floorCount,
            boolean enabled,
            String reason) {
    }
}
