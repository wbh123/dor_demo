package com.wust.dormitory.platform;

import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.EntitlementAdminService;
import com.wust.dormitory.subscription.PlanService;
import com.wust.dormitory.subscription.QuotaService;
import com.wust.dormitory.subscription.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformManagementController {
    private final PlanService planService;
    private final SubscriptionService subscriptionService;
    private final EntitlementAdminService entitlementAdminService;
    private final QuotaService quotaService;

    public PlatformManagementController(PlanService planService,
                                        SubscriptionService subscriptionService,
                                        EntitlementAdminService entitlementAdminService,
                                        QuotaService quotaService) {
        this.planService = planService;
        this.subscriptionService = subscriptionService;
        this.entitlementAdminService = entitlementAdminService;
        this.quotaService = quotaService;
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
    public ResponseEntity<Map<String, Object>> revisePlan(@PathVariable long sourceRevisionId,
                                                           @RequestBody PlanRevisionRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        long revisionId = planService.createRevision(sourceRevisionId, request.revisionName(),
                request.description(), request.features(), request.quotas(), request.reason(), operator);
        return ResponseEntity.ok(Map.of("revisionId", revisionId));
    }

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionService.CurrentSubscription> subscription() {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(subscriptionService.currentSubscription());
    }

    @GetMapping("/subscription/history")
    public ResponseEntity<List<Map<String, Object>>> subscriptionHistory() {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(subscriptionService.history());
    }

    @GetMapping("/subscription/preview")
    public ResponseEntity<SubscriptionService.ChangePreview> preview(
            @RequestParam long targetPlanRevisionId) {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(subscriptionService.previewChange(targetPlanRevisionId));
    }

    @PostMapping("/subscription/plan")
    public ResponseEntity<SubscriptionService.CurrentSubscription> changePlan(
            @RequestBody SubscriptionPlanChangeRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        String operation = "DOWNGRADE".equalsIgnoreCase(request.direction())
                ? "SUBSCRIPTION_DOWNGRADE" : "SUBSCRIPTION_UPGRADE";
        return ResponseEntity.ok(subscriptionService.changePlan(request.planRevisionId(), operation,
                request.reason(), request.contractNumber(), operator));
    }

    @PostMapping("/subscription/status")
    public ResponseEntity<SubscriptionService.CurrentSubscription> changeStatus(
            @RequestBody SubscriptionStatusRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        String action = request.action() == null ? "" : request.action().toUpperCase();
        return ResponseEntity.ok(switch (action) {
            case "SUSPEND" -> subscriptionService.changeStatus("SUSPENDED", false,
                    "SUBSCRIPTION_SUSPEND", request.reason(), operator);
            case "RESUME" -> subscriptionService.changeStatus("ACTIVE", false,
                    "SUBSCRIPTION_RESUME", request.reason(), operator);
            case "TERMINATE" -> subscriptionService.changeStatus("TERMINATED", false,
                    "SUBSCRIPTION_TERMINATE", request.reason(), operator);
            case "EMERGENCY_STOP" -> subscriptionService.changeStatus(
                    subscriptionService.currentSubscription().serviceStatus(), true,
                    "SUBSCRIPTION_EMERGENCY_STOP", request.reason(), operator);
            case "EMERGENCY_RESUME" -> subscriptionService.changeStatus(
                    subscriptionService.currentSubscription().serviceStatus(), false,
                    "SUBSCRIPTION_EMERGENCY_RESUME", request.reason(), operator);
            default -> throw new com.wust.dormitory.common.error.BusinessException(
                    "SUBSCRIPTION_ACTION_INVALID", "不支持的订阅状态操作");
        });
    }

    @GetMapping("/features")
    public ResponseEntity<List<Map<String, Object>>> features() {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(entitlementAdminService.features());
    }

    @GetMapping("/feature-overrides")
    public ResponseEntity<List<Map<String, Object>>> featureOverrides() {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(entitlementAdminService.featureOverrides());
    }

    @PostMapping("/feature-overrides")
    public ResponseEntity<Map<String, Object>> featureOverride(@RequestBody FeatureOverrideRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        long id = entitlementAdminService.addFeatureOverride(request.featureCode(), request.overrideType(),
                request.effectiveFrom(), request.effectiveUntil(), request.reason(), operator);
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
    public ResponseEntity<Map<String, Object>> quotaOverride(@RequestBody QuotaOverrideRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        long id = entitlementAdminService.addQuotaOverride(request.quotaCode(), request.quotaValue(),
                request.effectiveFrom(), request.effectiveUntil(), request.reason(), operator);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @GetMapping("/audit")
    public ResponseEntity<List<Map<String, Object>>> audit(@RequestParam(defaultValue = "100") int limit) {
        SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(entitlementAdminService.audit(limit));
    }

    public record PlanRequest(String planCode, String planName, String revisionName,
                              String description, List<String> features,
                              Map<String, Long> quotas, String reason) {
    }

    public record PlanRevisionRequest(String revisionName, String description,
                                      List<String> features, Map<String, Long> quotas,
                                      String reason) {
    }

    public record SubscriptionPlanChangeRequest(long planRevisionId, String direction,
                                                String contractNumber, String reason) {
    }

    public record SubscriptionStatusRequest(String action, String reason) {
    }

    public record FeatureOverrideRequest(String featureCode, String overrideType,
                                         LocalDateTime effectiveFrom, LocalDateTime effectiveUntil,
                                         String reason) {
    }

    public record QuotaOverrideRequest(String quotaCode, long quotaValue,
                                       LocalDateTime effectiveFrom, LocalDateTime effectiveUntil,
                                       String reason) {
    }
}
