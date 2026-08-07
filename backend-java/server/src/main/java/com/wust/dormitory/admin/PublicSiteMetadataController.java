package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/site-config")
public class PublicSiteMetadataController {
    private final SiteMetadataService service;

    public PublicSiteMetadataController(SiteMetadataService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ObjectSuccessResponse> get() {
        return ResponseEntity.ok(ResponseFactory.object(service.publicConfig()));
    }
}
