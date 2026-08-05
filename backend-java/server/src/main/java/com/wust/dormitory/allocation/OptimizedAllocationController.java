package com.wust.dormitory.allocation;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/admin")
public class OptimizedAllocationController {
    private final OptimizedAllocationRunService service;
    private final FeatureAccessService featureAccessService;

    public OptimizedAllocationController(
            OptimizedAllocationRunService service,
            FeatureAccessService featureAccessService) {
        this.service = service;
        this.featureAccessService = featureAccessService;
    }

    @PostMapping("/batches/{batchId}/allocation/optimized-runs")
    public ResponseEntity<ObjectSuccessResponse> createRun(
            @PathVariable long batchId,
            @RequestBody CreateRunRequest request) {
        featureAccessService.require(FeatureCodes.P2_ALLOCATION_OPTIMIZED_EXECUTE);
        CurrentUser operator = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.createRun(
                batchId,
                request.randomSeed(),
                Duration.ofMinutes(request.ttlMinutes()),
                operator)));
    }

    @GetMapping("/allocation/optimized-runs/{runId}")
    public ResponseEntity<ObjectSuccessResponse> getRun(@PathVariable long runId) {
        featureAccessService.require(FeatureCodes.P2_ALLOCATION_OPTIMIZED_EXECUTE);
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.run(runId)));
    }

    @PostMapping("/allocation/optimized-runs/{runId}/local-swaps")
    public ResponseEntity<ObjectSuccessResponse> localSwap(
            @PathVariable long runId,
            @RequestBody LocalSwapRequest request) {
        featureAccessService.require(FeatureCodes.P2_ALLOCATION_LOCAL_SWAP);
        return ResponseEntity.ok(ResponseFactory.object(service.localSwap(
                runId,
                request.leftStudentId(),
                request.rightStudentId(),
                request.expectedVersion(),
                request.reason(),
                SecurityUsers.requireAdmin())));
    }

    @PostMapping("/allocation/optimized-runs/{runId}/commit")
    public ResponseEntity<ObjectSuccessResponse> commit(@PathVariable long runId) {
        featureAccessService.require(FeatureCodes.P2_ALLOCATION_OPTIMIZED_EXECUTE);
        return ResponseEntity.ok(ResponseFactory.object(
                service.commit(runId, SecurityUsers.requireAdmin())));
    }

    @GetMapping("/allocation/optimized-runs/{runId}/fairness-comparison")
    public ResponseEntity<ObjectSuccessResponse> fairnessComparison(@PathVariable long runId) {
        featureAccessService.require(FeatureCodes.P2_FAIRNESS_COMPARISON);
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.fairnessComparison(runId)));
    }

    @GetMapping("/allocation/optimized-runs/{runId}/export.csv")
    public ResponseEntity<byte[]> export(
            @PathVariable long runId,
            @RequestParam(defaultValue = "false") boolean includeSensitiveData) {
        featureAccessService.require(FeatureCodes.P2_ALLOCATION_EXPERIMENT_EXPORT);
        SecurityUsers.requireAdmin();
        String csv = service.exportCsv(runId);
        byte[] body = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("optimized-allocation-run-" + runId + ".csv", StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    public record CreateRunRequest(long randomSeed, int ttlMinutes) {
    }

    public record LocalSwapRequest(
            long leftStudentId,
            long rightStudentId,
            int expectedVersion,
            String reason) {
    }
}
