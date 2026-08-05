package com.wust.dormitory.report;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.export.ExportTaskService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds reports from fixed metadata only; it never accepts arbitrary SQL. */
@Service
public class ReportBuilderService {
    public static final Set<String> FIELD_WHITELIST = Set.of(
            "batchCode", "batchName", "academicYear", "majorName", "gradeYear",
            "degreeLevel", "studentCategory", "campusName", "buildingName", "roomType",
            "participantCount", "selfSelectionCount", "teamSelectionCount",
            "unifiedAllocationCount", "unassignedCount", "averageMatchScore",
            "bedUtilizationRate", "completionDurationSeconds");
    public static final Set<String> FILTER_WHITELIST = Set.of(
            "academicYear", "batchId", "majorId", "gradeYear", "degreeLevel",
            "studentCategory", "campusId", "buildingId", "roomType");
    public static final Set<String> SORT_WHITELIST = Set.of(
            "academicYear", "batchCode", "participantCount", "averageMatchScore",
            "bedUtilizationRate", "completionDurationSeconds");
    public static final Set<String> PRESET_METRICS = Set.of(
            "participantCount", "selfSelectionCount", "teamSelectionCount",
            "unifiedAllocationCount", "unassignedCount", "recommendationAdoptionCount",
            "averageMatchScore", "minimumMatchScore", "roomChangeCount", "exchangeCount",
            "waitlistRequestCount", "waitlistAssignmentCount", "bedUtilizationRate",
            "manualAdjustmentCount", "anomalyCount", "completionDurationSeconds");

    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureAccessService featureAccessService;
    private final ExportTaskService exportTaskService;
    private final ObjectMapper objectMapper;

    public ReportBuilderService(
            NamedParameterJdbcTemplate jdbc,
            FeatureAccessService featureAccessService,
            ExportTaskService exportTaskService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.featureAccessService = featureAccessService;
        this.exportTaskService = exportTaskService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> metadata() {
        featureAccessService.require(FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        return Map.of(
                "fields", FIELD_WHITELIST.stream().sorted().toList(),
                "filters", FILTER_WHITELIST.stream().sorted().toList(),
                "sorts", SORT_WHITELIST.stream().sorted().toList(),
                "metrics", PRESET_METRICS.stream().sorted().toList(),
                "arbitraryQueryAllowed", false);
    }

    public Map<String, Object> saveTemplate(
            Long templateId,
            ReportDefinition definition,
            String reason,
            CurrentUser operator) {
        featureAccessService.require(FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        ReportDefinition normalized = validate(definition);
        String normalizedReason = requireReason(reason);
        if (templateId == null) {
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO report_template
                    (template_name, definition_json, enabled,
                     created_by, creation_reason)
                    VALUES (:name,CAST(:definition AS JSON),1,:createdBy,:reason)
                    """, new MapSqlParameterSource()
                    .addValue("name", normalized.name())
                    .addValue("definition", json(normalized))
                    .addValue("createdBy", operator.userId())
                    .addValue("reason", normalizedReason),
                    keys,
                    new String[]{"id"});
            Number key = keys.getKey();
            if (key == null) throw new IllegalStateException("报表模板未返回编号");
            templateId = key.longValue();
        } else {
            jdbc.update("""
                    UPDATE report_template
                    SET template_name=:name, definition_json=CAST(:definition AS JSON),
                        updated_by=:updatedBy, update_reason=:reason,
                        updated_at=CURRENT_TIMESTAMP(3)
                    WHERE id=:id
                    """, new MapSqlParameterSource()
                    .addValue("id", templateId)
                    .addValue("name", normalized.name())
                    .addValue("definition", json(normalized))
                    .addValue("updatedBy", operator.userId())
                    .addValue("reason", normalizedReason));
        }
        return Map.of("id", templateId, "definition", normalized);
    }

    public Map<String, Object> requestExport(
            ReportDefinition definition,
            String reason,
            CurrentUser operator) {
        featureAccessService.require(FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        ReportDefinition normalized = validate(definition);
        return exportTaskService.create(
                "CUSTOM_REPORT",
                json(normalized),
                requireReason(reason),
                operator);
    }

    public List<Map<String, Object>> templates() {
        featureAccessService.require(FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        return jdbc.queryForList("""
                SELECT id, template_name, definition_json, enabled,
                       created_by, creation_reason, created_at, updated_at
                FROM report_template
                WHERE enabled=1
                ORDER BY updated_at DESC, id DESC
                """, Map.of());
    }

    private ReportDefinition validate(ReportDefinition definition) {
        ReportDefinition normalized = definition.normalized();
        requireSubset(normalized.fields(), FIELD_WHITELIST, "报表字段");
        requireSubset(normalized.filters().keySet(), FILTER_WHITELIST, "筛选条件");
        requireSubset(normalized.sorts(), SORT_WHITELIST, "排序字段");
        requireSubset(normalized.metrics(), PRESET_METRICS, "预设指标");
        if (normalized.fields().isEmpty() && normalized.metrics().isEmpty()) {
            throw new BusinessException("REPORT_DEFINITION_EMPTY", "至少选择一个字段或指标");
        }
        return normalized;
    }

    private void requireSubset(Set<String> values, Set<String> whitelist, String label) {
        Set<String> invalid = new LinkedHashSet<>(values);
        invalid.removeAll(whitelist);
        if (!invalid.isEmpty()) {
            throw new BusinessException(
                    "REPORT_WHITELIST_VIOLATION",
                    label + "不在白名单中：" + String.join(",", invalid),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.trim().length() < 2) {
            throw new BusinessException("REPORT_REASON_REQUIRED", "保存或生成报表必须填写原因");
        }
        return reason.trim();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("报表定义无法序列化", exception);
        }
    }

    public record ReportDefinition(
            String name,
            Set<String> fields,
            Map<String, Object> filters,
            Set<String> sorts,
            Set<String> metrics,
            String locale) {
        ReportDefinition normalized() {
            return new ReportDefinition(
                    name == null || name.isBlank() ? "未命名报表" : name.trim(),
                    fields == null ? Set.of() : Set.copyOf(fields),
                    filters == null ? Map.of() : Map.copyOf(filters),
                    sorts == null ? Set.of() : Set.copyOf(sorts),
                    metrics == null ? Set.of() : Set.copyOf(metrics),
                    "en-US".equals(locale) ? "en-US" : "zh-CN");
        }
    }
}
