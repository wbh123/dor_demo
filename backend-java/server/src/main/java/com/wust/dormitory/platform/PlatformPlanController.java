package com.wust.dormitory.platform;

import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.PlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformPlanController {
    private final PlanService planService;

    public PlatformPlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping("/plans")
    public ResponseEntity<List<Map<String, Object>>> plans() {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(planService.listPlans());
    }

    @GetMapping("/plans/revisions/{revisionId}")
    public ResponseEntity<Map<String, Object>> planRevision(@PathVariable long revisionId) {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(planService.revision(revisionId));
    }

    @PostMapping("/plans")
    public ResponseEntity<Map<String, Object>> createPlan(@RequestBody PlanRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        long revisionId = planService.createPlan(request.planCode(), request.planName(),
                request.revisionName(), request.description(), request.features(), request.quotas(),
                request.reason(), operator);
        return ResponseEntity.ok(Map.of("revisionId", revisionId));
    }

    @PostMapping("/plans/revisions/{sourceRevisionId}")
    public ResponseEntity<Map<String, Object>> revisePlan(
            @PathVariable long sourceRevisionId,
            @RequestBody PlanRevisionRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        long revisionId = planService.createRevision(sourceRevisionId, request.revisionName(),
                request.description(), request.features(), request.quotas(), request.reason(), operator);
        return ResponseEntity.ok(Map.of("revisionId", revisionId));
    }

    public record PlanRequest(
            String planCode,
            String planName,
            String revisionName,
            String description,
            List<String> features,
            Map<String, Long> quotas,
            String reason) {
    }

    public record PlanRevisionRequest(
            String revisionName,
            String description,
            List<String> features,
            Map<String, Long> quotas,
            String reason) {
    }
}
