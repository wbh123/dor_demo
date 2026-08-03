package com.wust.dormitory.operations;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.OperationsApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationsController implements OperationsApi {
    private final OperationsService service;

    public OperationsController(OperationsService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getOperationsOverview() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.overview()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getOperationsHealth() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.health()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> previewOptimizedAllocation(
            Long batchId,
            Long randomSeed) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.optimizedPreview(
                batchId,
                randomSeed == null ? 2026L : randomSeed)));
    }
}
