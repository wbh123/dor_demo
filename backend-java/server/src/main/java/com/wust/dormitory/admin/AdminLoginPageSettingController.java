package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settings/login-page")
public class AdminLoginPageSettingController {
    private final SiteMetadataService service;

    public AdminLoginPageSettingController(SiteMetadataService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ObjectSuccessResponse> get() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.adminLoginConfig()));
    }

    @PutMapping
    public ResponseEntity<ObjectSuccessResponse> update(
            @RequestBody SiteMetadataService.LoginContentCommand command) {
        return ResponseEntity.ok(ResponseFactory.object(
                service.updateLoginForSchoolAdmin(command, SecurityUsers.requireAdmin())));
    }
}
