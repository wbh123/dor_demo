package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.PublicSiteMetadataApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicSiteMetadataController implements PublicSiteMetadataApi {
    private final SiteMetadataService service;

    public PublicSiteMetadataController(SiteMetadataService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getPublicSiteConfig() {
        return ResponseEntity.ok(ResponseFactory.object(service.publicConfig()));
    }
}
