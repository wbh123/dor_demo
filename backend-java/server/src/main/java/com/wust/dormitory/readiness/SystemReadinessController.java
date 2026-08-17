package com.wust.dormitory.readiness;

import com.wust.dormitory.model.readiness.api.SystemReadinessApi;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
public class SystemReadinessController implements SystemReadinessApi {
    private final SystemReadinessService service;

    public SystemReadinessController(SystemReadinessService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<com.wust.dormitory.model.readiness.dto.SystemReadinessReport> getSystemReadiness() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(toApi(service.check()));
    }

    private com.wust.dormitory.model.readiness.dto.SystemReadinessReport toApi(SystemReadinessReport report) {
        com.wust.dormitory.model.readiness.dto.SystemReadinessReport dto =
                new com.wust.dormitory.model.readiness.dto.SystemReadinessReport();
        dto.setOverallStatus(com.wust.dormitory.model.readiness.dto.SystemReadinessReport.OverallStatusEnum
                .fromValue(report.overallStatus().name()));
        dto.setCheckedAt(Date.from(report.checkedAt()));
        dto.setSummary(toApi(report.summary()));
        dto.setCategories(report.categories());
        dto.setChecks(report.checks().stream().map(this::toApi).toList());
        return dto;
    }

    private com.wust.dormitory.model.readiness.dto.SystemReadinessSummary toApi(SystemReadinessSummary summary) {
        com.wust.dormitory.model.readiness.dto.SystemReadinessSummary dto =
                new com.wust.dormitory.model.readiness.dto.SystemReadinessSummary();
        dto.setTotal(summary.total());
        dto.setPassed(summary.passed());
        dto.setInfo(summary.info());
        dto.setWarnings(summary.warnings());
        dto.setErrors(summary.errors());
        dto.setBlocking(summary.blocking());
        return dto;
    }

    private com.wust.dormitory.model.readiness.dto.ReadinessCheckResult toApi(ReadinessCheckResult result) {
        com.wust.dormitory.model.readiness.dto.ReadinessCheckResult dto =
                new com.wust.dormitory.model.readiness.dto.ReadinessCheckResult();
        dto.setCode(result.code());
        dto.setCategory(result.category());
        dto.setTitle(result.title());
        dto.setSeverity(com.wust.dormitory.model.readiness.dto.ReadinessCheckResult.SeverityEnum
                .fromValue(result.severity().name()));
        dto.setBlocking(result.blocking());
        dto.setStatus(result.status());
        dto.setSummary(result.summary());
        dto.setEvidence(result.evidence());
        dto.setSuggestedAction(result.suggestedAction());
        dto.setActionRoute(result.actionRoute());
        dto.setCheckedAt(Date.from(result.checkedAt()));
        return dto;
    }
}
