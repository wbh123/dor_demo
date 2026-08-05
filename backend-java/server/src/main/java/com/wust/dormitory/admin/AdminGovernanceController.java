package com.wust.dormitory.admin;

import com.wust.dormitory.analytics.BatchAnalyticsSnapshotService;
import com.wust.dormitory.analytics.HistoricalAnalyticsService;
import com.wust.dormitory.audit.AuditExportService;
import com.wust.dormitory.audit.AuditQueryService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.export.ExportTaskService;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.notification.NotificationDispatchService;
import com.wust.dormitory.notification.NotificationRecipientResolver;
import com.wust.dormitory.notification.NotificationTemplateService;
import com.wust.dormitory.report.ReportBuilderService;
import com.wust.dormitory.retention.DataRetentionQueryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/governance")
public class AdminGovernanceController {
    private final AuditQueryService auditQueryService;
    private final AuditExportService auditExportService;
    private final ExportTaskService exportTaskService;
    private final NotificationTemplateService notificationTemplateService;
    private final NotificationDispatchService notificationDispatchService;
    private final BatchAnalyticsSnapshotService snapshotService;
    private final HistoricalAnalyticsService historicalAnalyticsService;
    private final ReportBuilderService reportBuilderService;
    private final DataRetentionQueryService retentionService;
    private final FeatureAccessService featureAccessService;

    public AdminGovernanceController(
            AuditQueryService auditQueryService,
            AuditExportService auditExportService,
            ExportTaskService exportTaskService,
            NotificationTemplateService notificationTemplateService,
            NotificationDispatchService notificationDispatchService,
            BatchAnalyticsSnapshotService snapshotService,
            HistoricalAnalyticsService historicalAnalyticsService,
            ReportBuilderService reportBuilderService,
            DataRetentionQueryService retentionService,
            FeatureAccessService featureAccessService) {
        this.auditQueryService = auditQueryService;
        this.auditExportService = auditExportService;
        this.exportTaskService = exportTaskService;
        this.notificationTemplateService = notificationTemplateService;
        this.notificationDispatchService = notificationDispatchService;
        this.snapshotService = snapshotService;
        this.historicalAnalyticsService = historicalAnalyticsService;
        this.reportBuilderService = reportBuilderService;
        this.retentionService = retentionService;
        this.featureAccessService = featureAccessService;
    }

    @PostMapping("/audit/query")
    public ResponseEntity<ObjectSuccessResponse> auditQuery(@RequestBody AuditQueryService.AuditQuery query) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(auditQueryService.query(query)));
    }

    @PostMapping("/audit/export")
    public ResponseEntity<ObjectSuccessResponse> auditExport(@RequestBody AuditExportRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(auditExportService.request(
                request.query(), request.includeSensitiveData(), request.reason(), SecurityUsers.requireAdmin())));
    }

    @GetMapping("/exports")
    public ResponseEntity<ListSuccessResponse> exportTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        SecurityUsers.requireAdmin();
        requireAny(FeatureCodes.P2_AUDIT_EXPORT, FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        return ResponseEntity.ok(ResponseFactory.list(exportTaskService.list(page, size)));
    }

    @PostMapping("/exports/{taskId}/cancel")
    public ResponseEntity<ObjectSuccessResponse> cancelExport(@PathVariable long taskId) {
        requireAny(FeatureCodes.P2_AUDIT_EXPORT, FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        exportTaskService.cancel(taskId, SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(exportTaskService.get(taskId)));
    }

    @GetMapping("/notifications/templates")
    public ResponseEntity<ListSuccessResponse> templates() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(notificationTemplateService.list()));
    }

    @PostMapping("/notifications/templates/revisions")
    public ResponseEntity<ObjectSuccessResponse> createTemplate(@RequestBody NotificationTemplateRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(notificationTemplateService.createRevision(
                request.templateId(),
                new NotificationTemplateService.TemplateCommand(
                        request.templateCode(), request.templateName(),
                        request.titleZhCn(), request.contentZhCn(),
                        request.titleEnUs(), request.contentEnUs(),
                        request.enabled(), request.creationReason()),
                SecurityUsers.requireAdmin())));
    }

    @PostMapping("/notifications/preflight")
    public ResponseEntity<ObjectSuccessResponse> notificationPreflight(@RequestBody NotificationSendRequest request) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(notificationDispatchService.preflight(
                request.criteria(), request.templateRevisionId(), request.variables())));
    }

    @PostMapping("/notifications/schedule")
    public ResponseEntity<ObjectSuccessResponse> scheduleNotification(@RequestBody NotificationSendRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(notificationDispatchService.schedule(
                request.criteria(), request.templateRevisionId(), request.variables(),
                request.scheduledAt(), request.zoneId(), request.reason(), SecurityUsers.requireAdmin())));
    }

    @GetMapping("/notifications/status")
    public ResponseEntity<ListSuccessResponse> notificationStatus(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(notificationDispatchService.status(page, size)));
    }

    @PostMapping("/notifications/{taskId}/cancel")
    public ResponseEntity<ObjectSuccessResponse> cancelNotification(@PathVariable long taskId) {
        notificationDispatchService.cancel(taskId, SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", taskId, "status", "CANCELLED")));
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
    public ResponseEntity<ObjectSuccessResponse> dashboard(@RequestBody HistoricalAnalyticsService.AnalyticsFilter filter) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(historicalAnalyticsService.dashboard(filter)));
    }

    @PostMapping("/analytics/comparison")
    public ResponseEntity<ObjectSuccessResponse> comparison(@RequestBody HistoricalAnalyticsService.AnalyticsFilter filter) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(historicalAnalyticsService.comparison(filter)));
    }

    @PostMapping("/analytics/trend")
    public ResponseEntity<ObjectSuccessResponse> trend(@RequestBody HistoricalAnalyticsService.AnalyticsFilter filter) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(historicalAnalyticsService.trend(filter)));
    }

    @GetMapping("/reports/metadata")
    public ResponseEntity<ObjectSuccessResponse> reportMetadata() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(reportBuilderService.metadata()));
    }

    @GetMapping("/reports/templates")
    public ResponseEntity<ListSuccessResponse> reportTemplates() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(reportBuilderService.templates()));
    }

    @PostMapping("/reports/templates")
    public ResponseEntity<ObjectSuccessResponse> saveReportTemplate(@RequestBody ReportTemplateRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(reportBuilderService.saveTemplate(
                request.templateId(), request.definition(), request.reason(), SecurityUsers.requireAdmin())));
    }

    @PostMapping("/reports/export")
    public ResponseEntity<ObjectSuccessResponse> exportReport(@RequestBody ReportTemplateRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(reportBuilderService.requestExport(
                request.definition(), request.reason(), SecurityUsers.requireAdmin())));
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

    private void requireAny(String... featureCodes) {
        for (String featureCode : featureCodes) {
            if (featureAccessService.has(featureCode)) return;
        }
        throw new BusinessException(
                "FEATURE_NOT_ENABLED",
                "当前服务未开通该治理功能",
                HttpStatus.FORBIDDEN);
    }

    public record AuditExportRequest(AuditQueryService.AuditQuery query, boolean includeSensitiveData, String reason) {}
    public record NotificationTemplateRequest(
            Long templateId, String templateCode, String templateName,
            String titleZhCn, String contentZhCn, String titleEnUs, String contentEnUs,
            boolean enabled, String creationReason) {}
    public record NotificationSendRequest(
            NotificationRecipientResolver.RecipientCriteria criteria,
            long templateRevisionId, Map<String, Object> variables,
            LocalDateTime scheduledAt, String zoneId, String reason) {}
    public record ReportTemplateRequest(
            Long templateId, ReportBuilderService.ReportDefinition definition, String reason) {}
    public record ReasonRequest(String reason) {}
}
