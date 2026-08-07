package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.AdminSiteMetadataApi;
import com.wust.dormitory.model.dto.LoginPageContentUpdateRequest;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminLoginPageSettingController implements AdminSiteMetadataApi {
    private final SiteMetadataService service;

    public AdminLoginPageSettingController(SiteMetadataService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getAdminLoginPageSetting() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.adminLoginConfig()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateAdminLoginPageSetting(
            LoginPageContentUpdateRequest request) {
        var command = new SiteMetadataService.LoginContentCommand(
                request.getHtml(),
                request.getImageUrl());
        return ResponseEntity.ok(ResponseFactory.object(
                service.updateLoginForSchoolAdmin(command, SecurityUsers.requireAdmin())));
    }
}
