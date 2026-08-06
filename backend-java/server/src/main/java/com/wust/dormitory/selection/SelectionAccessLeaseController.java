package com.wust.dormitory.selection;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import com.wust.dormitory.subscription.QuotaCodes;
import com.wust.dormitory.subscription.QuotaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class SelectionAccessLeaseController {
    private static final Duration LEASE_TTL = Duration.ofSeconds(75);

    private final ConcurrentSelectionLeaseService leaseService;
    private final FeatureAccessService featureAccessService;
    private final QuotaService quotaService;

    public SelectionAccessLeaseController(
            ConcurrentSelectionLeaseService leaseService,
            FeatureAccessService featureAccessService,
            QuotaService quotaService) {
        this.leaseService = leaseService;
        this.featureAccessService = featureAccessService;
        this.quotaService = quotaService;
    }

    @PostMapping("/student/selection-leases")
    public ResponseEntity<ObjectSuccessResponse> acquire() {
        CurrentUser user = SecurityUsers.requireStudent();
        if (!featureAccessService.has(FeatureCodes.P2_CONCURRENT_SELECTION_LIMIT)) {
            return ResponseEntity.ok(ResponseFactory.object(Map.of("limited", false)));
        }
        int maxUsers = maxUsers();
        ConcurrentSelectionLeaseService.Lease lease =
                leaseService.acquire(user.studentId(), maxUsers, LEASE_TTL);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("limited", true);
        result.put("token", lease.token());
        result.put("expiresAt", lease.expiresAt());
        result.put("activeUsers", lease.activeUsers());
        result.put("maxUsers", maxUsers);
        result.put("heartbeatSeconds", 25);
        return ResponseEntity.ok(ResponseFactory.object(result));
    }

    @PutMapping("/student/selection-leases/{token}")
    public ResponseEntity<ObjectSuccessResponse> renew(@PathVariable String token) {
        CurrentUser user = SecurityUsers.requireStudent();
        if (!featureAccessService.has(FeatureCodes.P2_CONCURRENT_SELECTION_LIMIT)) {
            return ResponseEntity.ok(ResponseFactory.object(Map.of("limited", false)));
        }
        ConcurrentSelectionLeaseService.Lease lease =
                leaseService.renew(user.studentId(), token, LEASE_TTL);
        return ResponseEntity.ok(ResponseFactory.object(Map.of(
                "limited", true,
                "token", lease.token(),
                "expiresAt", lease.expiresAt(),
                "activeUsers", lease.activeUsers(),
                "maxUsers", maxUsers(),
                "heartbeatSeconds", 25)));
    }

    @DeleteMapping("/student/selection-leases/{token}")
    public ResponseEntity<ObjectSuccessResponse> release(@PathVariable String token) {
        CurrentUser user = SecurityUsers.requireStudent();
        if (!featureAccessService.has(FeatureCodes.P2_CONCURRENT_SELECTION_LIMIT)) {
            return ResponseEntity.ok(ResponseFactory.object(Map.of("limited", false)));
        }
        int activeUsers = leaseService.release(user.studentId(), token);
        return ResponseEntity.ok(ResponseFactory.object(Map.of(
                "limited", true,
                "activeUsers", activeUsers,
                "maxUsers", maxUsers())));
    }

    @GetMapping("/admin/settings/selection-concurrency")
    public ResponseEntity<ObjectSuccessResponse> status() {
        SecurityUsers.requireAdmin();
        boolean enabled = featureAccessService.has(FeatureCodes.P2_CONCURRENT_SELECTION_LIMIT);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", enabled);
        result.put("maxUsers", enabled ? maxUsers() : 0);
        result.put("activeUsers", enabled ? leaseService.activeUsers() : 0);
        result.put("editable", false);
        return ResponseEntity.ok(ResponseFactory.object(result));
    }

    private int maxUsers() {
        Long value = quotaService.currentQuotas().get(QuotaCodes.MAX_CONCURRENT_SELECTION_USERS);
        if (value == null || value < 1 || value > Integer.MAX_VALUE) {
            throw new com.wust.dormitory.common.error.BusinessException(
                    "CONCURRENT_SELECTION_QUOTA_NOT_CONFIGURED",
                    "系统管理员尚未配置学校选寝并发上限",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
        return value.intValue();
    }
}
