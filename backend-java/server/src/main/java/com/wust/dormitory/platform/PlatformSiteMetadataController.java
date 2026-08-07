package com.wust.dormitory.platform;

import com.wust.dormitory.admin.SiteMetadataService;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.PlatformSiteMetadataApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.PlatformSiteMetadataUpdateRequest;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformSiteMetadataController implements PlatformSiteMetadataApi {
    private final SiteMetadataService service;

    public PlatformSiteMetadataController(SiteMetadataService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getPlatformSiteMetadata() {
        SecurityUsers.requireSystemAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.platformConfig()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updatePlatformSiteMetadata(
            PlatformSiteMetadataUpdateRequest request) {
        var branding = request.getBranding();
        var login = request.getLogin();
        var command = new SiteMetadataService.PlatformSiteCommand(
                new SiteMetadataService.BrandingCommand(
                        branding.getSchoolName(),
                        branding.getSquareLogoUrl(),
                        branding.getHorizontalLogoUrl()),
                new SiteMetadataService.LoginContentCommand(
                        login.getHtml(),
                        login.getImageUrl()),
                Boolean.TRUE.equals(request.getSchoolAdminEditable()));
        return ResponseEntity.ok(ResponseFactory.object(
                service.updatePlatform(command, SecurityUsers.requireSystemAdmin())));
    }
}
