package com.wust.dormitory.operations;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.OperationsApi;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationsController implements OperationsApi {
    private final OperationsService service;
    private final RedisRecoveryService redisRecoveryService;
    private final AnomalyWorkbenchService anomalyWorkbenchService;

    public OperationsController(
            OperationsService service,
            RedisRecoveryService redisRecoveryService,
            AnomalyWorkbenchService anomalyWorkbenchService) {
        this.service = service;
        this.redisRecoveryService = redisRecoveryService;
        this.anomalyWorkbenchService = anomalyWorkbenchService;
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

    @GetMapping("/api/v1/admin/operations/redis-recovery/preview")
    public ResponseEntity<ObjectSuccessResponse> previewRedisRecovery() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(redisRecoveryService.previewRecovery()));
    }

    @PostMapping("/api/v1/admin/operations/redis-recovery/execute")
    public ResponseEntity<ObjectSuccessResponse> executeRedisRecovery() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(redisRecoveryService.executeRecovery()));
    }

    @GetMapping("/api/v1/admin/operations/anomalies")
    public ResponseEntity<ListSuccessResponse> listAnomalies(
            @RequestParam(required = false, defaultValue = "ALL") String type,
            @RequestParam(required = false, defaultValue = "ALL") String severity) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(anomalyWorkbenchService.listAnomalies(type, severity)));
    }

    @GetMapping("/api/v1/admin/operations/anomalies/summary")
    public ResponseEntity<ObjectSuccessResponse> anomalySummary() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(anomalyWorkbenchService.summary()));
    }
}
