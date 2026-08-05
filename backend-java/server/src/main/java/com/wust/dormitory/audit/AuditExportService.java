package com.wust.dormitory.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.export.ExportTaskService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SensitiveDataPolicyService;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditExportService {
    private final FeatureAccessService featureAccessService;
    private final SensitiveDataPolicyService sensitiveDataPolicyService;
    private final ExportTaskService exportTaskService;
    private final ObjectMapper objectMapper;

    public AuditExportService(
            FeatureAccessService featureAccessService,
            SensitiveDataPolicyService sensitiveDataPolicyService,
            ExportTaskService exportTaskService,
            ObjectMapper objectMapper) {
        this.featureAccessService = featureAccessService;
        this.sensitiveDataPolicyService = sensitiveDataPolicyService;
        this.exportTaskService = exportTaskService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> request(
            AuditQueryService.AuditQuery query,
            boolean includeSensitiveData,
            String reason,
            CurrentUser operator) {
        featureAccessService.require(FeatureCodes.P2_AUDIT_EXPORT);
        if (includeSensitiveData) {
            sensitiveDataPolicyService.requireSensitiveExport(operator, reason);
        } else {
            featureAccessService.require(FeatureCodes.P2_EXPORT_DESENSITIZATION);
        }
        return exportTaskService.create(
                "AUDIT_EXPORT",
                json(Map.of(
                        "query", query.normalized().asMap(),
                        "includeSensitiveData", includeSensitiveData)),
                sensitiveDataPolicyService.requireReason(reason),
                operator);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化审计导出条件", exception);
        }
    }
}
