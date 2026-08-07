package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditExportService;
import com.wust.dormitory.audit.AuditQueryService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.export.ExportTaskService;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/admin/governance")
public class AdminGovernanceAuditController {
    private final AuditQueryService auditQueryService;
    private final AuditExportService auditExportService;
    private final ExportTaskService exportTaskService;
    private final FeatureAccessService featureAccessService;

    public AdminGovernanceAuditController(
            AuditQueryService auditQueryService,
            AuditExportService auditExportService,
            ExportTaskService exportTaskService,
            FeatureAccessService featureAccessService) {
        this.auditQueryService = auditQueryService;
        this.auditExportService = auditExportService;
        this.exportTaskService = exportTaskService;
        this.featureAccessService = featureAccessService;
    }

    @PostMapping("/audit/query")
    public ResponseEntity<ObjectSuccessResponse> auditQuery(
            @RequestBody AuditQueryService.AuditQuery query) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(auditQueryService.query(query)));
    }

    @PostMapping("/audit/export")
    public ResponseEntity<ObjectSuccessResponse> auditExport(
            @RequestBody AuditExportRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(auditExportService.request(
                request.query(),
                request.includeSensitiveData(),
                request.reason(),
                SecurityUsers.requireAdmin())));
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

    @GetMapping("/exports/{taskId}/download")
    public ResponseEntity<Resource> downloadExport(
            @PathVariable long taskId,
            @RequestParam String token) {
        SecurityUsers.requireAdmin();
        requireAny(FeatureCodes.P2_AUDIT_EXPORT, FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        ExportTaskService.ExportDownload download = exportTaskService.download(taskId, token);
        String encoded = URLEncoder.encode(download.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .contentLength(download.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .body(new FileSystemResource(download.path()));
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

    public record AuditExportRequest(
            AuditQueryService.AuditQuery query,
            boolean includeSensitiveData,
            String reason) {
    }
}
