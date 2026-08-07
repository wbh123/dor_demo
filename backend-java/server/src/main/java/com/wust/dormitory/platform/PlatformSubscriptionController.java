package com.wust.dormitory.platform;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformSubscriptionController {
    private final SubscriptionService subscriptionService;

    public PlatformSubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
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
        return ResponseEntity.ok(subscriptionService.changePlan(
                request.planRevisionId(),
                operation,
                request.reason(),
                request.contractNumber(),
                operator));
    }

    @PostMapping("/subscription/status")
    public ResponseEntity<SubscriptionService.CurrentSubscription> changeStatus(
            @RequestBody SubscriptionStatusRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        String action = request.action() == null ? "" : request.action().toUpperCase();
        return ResponseEntity.ok(switch (action) {
            case "SUSPEND" -> subscriptionService.changeStatus(
                    "SUSPENDED", false, "SUBSCRIPTION_SUSPEND", request.reason(), operator);
            case "RESUME" -> subscriptionService.changeStatus(
                    "ACTIVE", false, "SUBSCRIPTION_RESUME", request.reason(), operator);
            case "TERMINATE" -> subscriptionService.changeStatus(
                    "TERMINATED", false, "SUBSCRIPTION_TERMINATE", request.reason(), operator);
            case "EMERGENCY_STOP" -> subscriptionService.changeStatus(
                    subscriptionService.currentSubscription().serviceStatus(),
                    true,
                    "SUBSCRIPTION_EMERGENCY_STOP",
                    request.reason(),
                    operator);
            case "EMERGENCY_RESUME" -> subscriptionService.changeStatus(
                    subscriptionService.currentSubscription().serviceStatus(),
                    false,
                    "SUBSCRIPTION_EMERGENCY_RESUME",
                    request.reason(),
                    operator);
            default -> throw new BusinessException(
                    "SUBSCRIPTION_ACTION_INVALID", "不支持的订阅状态操作");
        });
    }

    public record SubscriptionPlanChangeRequest(
            long planRevisionId,
            String direction,
            String contractNumber,
            String reason) {
    }

    public record SubscriptionStatusRequest(String action, String reason) {
    }
}
