package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/buildings")
public class BuildingManagementController {
    private final BuildingManagementService service;

    public BuildingManagementController(BuildingManagementService service) {
        this.service = service;
    }

    @GetMapping("/details")
    public ResponseEntity<ListSuccessResponse> listDetails() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.list()));
    }

    @PutMapping("/{buildingId}")
    public ResponseEntity<ObjectSuccessResponse> updateBuilding(
            @PathVariable long buildingId,
            @RequestBody BuildingUpdateRequest request) {
        BuildingManagementService.BuildingUpdateCommand command =
                new BuildingManagementService.BuildingUpdateCommand(
                        request.buildingCode(),
                        request.buildingName(),
                        request.gender(),
                        request.educationLevelScope(),
                        request.residentScope(),
                        request.floorCount() == null ? 0 : request.floorCount(),
                        Boolean.TRUE.equals(request.enabled()),
                        request.reason());
        return ResponseEntity.ok(ResponseFactory.object(
                service.update(buildingId, command, SecurityUsers.requireAdmin())));
    }
}
