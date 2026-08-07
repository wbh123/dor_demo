package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.report.ReportBuilderService;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/governance")
public class AdminGovernanceReportController {
    private final ReportBuilderService reportBuilderService;

    public AdminGovernanceReportController(ReportBuilderService reportBuilderService) {
        this.reportBuilderService = reportBuilderService;
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
    public ResponseEntity<ObjectSuccessResponse> saveReportTemplate(
            @RequestBody ReportTemplateRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(reportBuilderService.saveTemplate(
                request.templateId(),
                request.definition(),
                request.reason(),
                SecurityUsers.requireAdmin())));
    }

    @PostMapping("/reports/export")
    public ResponseEntity<ObjectSuccessResponse> exportReport(
            @RequestBody ReportTemplateRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(reportBuilderService.requestExport(
                request.definition(), request.reason(), SecurityUsers.requireAdmin())));
    }

    public record ReportTemplateRequest(
            Long templateId,
            ReportBuilderService.ReportDefinition definition,
            String reason) {
    }
}
