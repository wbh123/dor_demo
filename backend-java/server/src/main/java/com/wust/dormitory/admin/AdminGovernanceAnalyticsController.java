package com.wust.dormitory.admin;

import com.wust.dormitory.analytics.BatchAnalyticsSnapshotService;
import com.wust.dormitory.analytics.HistoricalAnalyticsService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/governance")
public class AdminGovernanceAnalyticsController {
    private final BatchAnalyticsSnapshotService snapshotService;
    private final HistoricalAnalyticsService historicalAnalyticsService;
    private final FeatureAccessService featureAccessService;

    public AdminGovernanceAnalyticsController(
            BatchAnalyticsSnapshotService snapshotService,
            HistoricalAnalyticsService historicalAnalyticsService,
            FeatureAccessService featureAccessService) {
        this.snapshotService = snapshotService;
        this.historicalAnalyticsService = historicalAnalyticsService;
        this.featureAccessService = featureAccessService;
    }

    @GetMapping("/analytics/definitions")
    public ResponseEntity<ObjectSuccessResponse> metricDefinitions() {
        SecurityUsers.requireAdmin();
        requireAny(
                FeatureCodes.P3_HISTORICAL_DASHBOARD,
                FeatureCodes.P3_CROSS_BATCH_COMPARISON,
                FeatureCodes.P3_TREND_ANALYSIS);
        return ResponseEntity.ok(ResponseFactory.object(Map.of(
                "items", snapshotService.definitions(),
                "metricVersion", BatchAnalyticsSnapshotService.METRIC_VERSION)));
    }

    @PostMapping("/analytics/batches/{batchId}/snapshot")
    public ResponseEntity<ObjectSuccessResponse> createSnapshot(@PathVariable long batchId) {
        SecurityUsers.requireAdmin();
        featureAccessService.require(FeatureCodes.P3_HISTORICAL_DASHBOARD);
        return ResponseEntity.ok(ResponseFactory.object(snapshotService.snapshot(batchId)));
    }

    @PostMapping("/analytics/dashboard")
    public ResponseEntity<ObjectSuccessResponse> dashboard(
            @RequestBody HistoricalAnalyticsService.AnalyticsFilter filter) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(historicalAnalyticsService.dashboard(filter)));
    }

    @PostMapping("/analytics/comparison")
    public ResponseEntity<ObjectSuccessResponse> comparison(
            @RequestBody HistoricalAnalyticsService.AnalyticsFilter filter) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(historicalAnalyticsService.comparison(filter)));
    }

    @PostMapping("/analytics/trend")
    public ResponseEntity<ObjectSuccessResponse> trend(
            @RequestBody HistoricalAnalyticsService.AnalyticsFilter filter) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(historicalAnalyticsService.trend(filter)));
    }

    private void requireAny(String... featureCodes) {
        for (String featureCode : featureCodes) {
            if (featureAccessService.has(featureCode)) return;
        }
        throw new BusinessException(
                "FEATURE_NOT_ENABLED",
                "当前服务未开通该治理功能",
                HttpStatus.FORBIDDEN);
    }
}
