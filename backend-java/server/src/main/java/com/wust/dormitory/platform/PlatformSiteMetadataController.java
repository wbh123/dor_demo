package com.wust.dormitory.platform;

import com.wust.dormitory.admin.SiteMetadataService;
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
@RequestMapping("/api/v1/platform/site-metadata")
public class PlatformSiteMetadataController {
    private final SiteMetadataService service;

    public PlatformSiteMetadataController(SiteMetadataService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ObjectSuccessResponse> get() {
        SecurityUsers.requireSystemAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.platformConfig()));
    }

    @PutMapping
    public ResponseEntity<ObjectSuccessResponse> update(
            @RequestBody SiteMetadataService.PlatformSiteCommand command) {
        return ResponseEntity.ok(ResponseFactory.object(
                service.updatePlatform(command, SecurityUsers.requireSystemAdmin())));
    }
}
