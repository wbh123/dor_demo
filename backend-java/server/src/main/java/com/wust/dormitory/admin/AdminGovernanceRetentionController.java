package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.retention.DataRetentionQueryService;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/governance")
public class AdminGovernanceRetentionController {
    private final DataRetentionQueryService retentionService;

    public AdminGovernanceRetentionController(DataRetentionQueryService retentionService) {
        this.retentionService = retentionService;
    }

    @GetMapping("/retention/policy")
    public ResponseEntity<ObjectSuccessResponse> retentionPolicy() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(retentionService.policy()));
    }

    @GetMapping("/retention/statistics")
    public ResponseEntity<ObjectSuccessResponse> retentionStatistics() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(retentionService.expiringStatistics()));
    }

    @GetMapping("/retention/simulate")
    public ResponseEntity<ObjectSuccessResponse> simulateRetention() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(retentionService.simulate()));
    }

    @PostMapping("/retention/preflight")
    public ResponseEntity<ObjectSuccessResponse> retentionPreflight(@RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(retentionService.preflight(
                SecurityUsers.requireAdmin(), request.reason())));
    }

    public record ReasonRequest(String reason) {
    }
}
