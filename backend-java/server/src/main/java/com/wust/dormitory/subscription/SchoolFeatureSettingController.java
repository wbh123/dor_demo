package com.wust.dormitory.subscription;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings/features")
public class SchoolFeatureSettingController {
    private final SchoolFeatureSettingService service;

    public SchoolFeatureSettingController(SchoolFeatureSettingService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ListSuccessResponse> list() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.list()));
    }

    @PutMapping("/{featureCode}")
    public ResponseEntity<ObjectSuccessResponse> update(
            @PathVariable String featureCode,
            @RequestBody UpdateRequest request) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.update(
                featureCode,
                request.enabled(),
                request.expectedVersion(),
                request.reason(),
                request.highRiskConfirmed(),
                operator)));
    }

    public record UpdateRequest(
            boolean enabled,
            int expectedVersion,
            String reason,
            boolean highRiskConfirmed) {
    }
}
