package com.wust.dormitory.platform;

import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.EntitlementAdminService;
import com.wust.dormitory.subscription.QuotaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformEntitlementController {
    private final EntitlementAdminService entitlementAdminService;
    private final QuotaService quotaService;

    public PlatformEntitlementController(
            EntitlementAdminService entitlementAdminService,
            QuotaService quotaService) {
        this.entitlementAdminService = entitlementAdminService;
        this.quotaService = quotaService;
    }

    @GetMapping("/features")
    public ResponseEntity<List<Map<String, Object>>> features() {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(entitlementAdminService.features());
    }

    @GetMapping("/features/entitlements")
    public ResponseEntity<List<EntitlementAdminService.FeatureEntitlementView>> featureEntitlements(
            @RequestParam(defaultValue = "false") boolean includeFuture) {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(entitlementAdminService.featureEntitlements(includeFuture));
    }

    @PutMapping("/features/{featureCode}/state")
    public ResponseEntity<EntitlementAdminService.FeatureEntitlementView> setFeatureState(
            @PathVariable String featureCode,
            @RequestBody FeatureStateRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(entitlementAdminService.setFeatureState(
                featureCode, request.targetState(), request.reason(), operator));
    }

    @PostMapping("/features/batch-state")
    public ResponseEntity<List<EntitlementAdminService.FeatureEntitlementView>> setFeatureStates(
            @RequestBody BatchFeatureStateRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        List<EntitlementAdminService.FeatureStateChange> changes = request.changes().stream()
                .map(change -> new EntitlementAdminService.FeatureStateChange(
                        change.featureCode(), change.targetState()))
                .toList();
        return ResponseEntity.ok(entitlementAdminService.setFeatureStates(
                changes, request.reason(), operator));
    }

    @GetMapping("/feature-overrides")
    public ResponseEntity<List<Map<String, Object>>> featureOverrides() {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(entitlementAdminService.featureOverrides());
    }

    @PostMapping("/feature-overrides")
    public ResponseEntity<Map<String, Object>> featureOverride(
            @RequestBody FeatureOverrideRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        long id = entitlementAdminService.addFeatureOverride(
                request.featureCode(),
                request.overrideType(),
                request.effectiveFrom(),
                request.effectiveUntil(),
                request.reason(),
                operator);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @GetMapping("/quotas")
    public ResponseEntity<Map<String, Object>> quotas() {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(Map.of(
                "catalog", entitlementAdminService.quotas(),
                "overrides", entitlementAdminService.quotaOverrides(),
                "effective", quotaService.currentQuotas(),
                "usage", quotaService.usageSummary()
        ));
    }

    @PostMapping("/quota-overrides")
    public ResponseEntity<Map<String, Object>> quotaOverride(
            @RequestBody QuotaOverrideRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        long id = entitlementAdminService.addQuotaOverride(
                request.quotaCode(),
                request.quotaValue(),
                request.effectiveFrom(),
                request.effectiveUntil(),
                request.reason(),
                operator);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @GetMapping("/audit")
    public ResponseEntity<List<Map<String, Object>>> audit(
            @RequestParam(defaultValue = "100") int limit) {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(entitlementAdminService.audit(limit));
    }

    public record FeatureStateRequest(
            EntitlementAdminService.FeatureTargetState targetState,
            String reason) {
    }

    public record FeatureStateChangeRequest(
            String featureCode,
            EntitlementAdminService.FeatureTargetState targetState) {
    }

    public record BatchFeatureStateRequest(
            List<FeatureStateChangeRequest> changes,
            String reason) {
    }

    public record FeatureOverrideRequest(
            String featureCode,
            String overrideType,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            String reason) {
    }

    public record QuotaOverrideRequest(
            String quotaCode,
            long quotaValue,
            LocalDateTime effectiveFrom,
            LocalDateTime effectiveUntil,
            String reason) {
    }
}
