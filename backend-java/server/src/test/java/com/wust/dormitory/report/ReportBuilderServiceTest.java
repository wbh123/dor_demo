package com.wust.dormitory.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.export.ExportTaskService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ReportBuilderServiceTest {
    private FeatureAccessService featureAccessService;
    private ExportTaskService exportTaskService;
    private ReportBuilderService service;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        featureAccessService = mock(FeatureAccessService.class);
        exportTaskService = mock(ExportTaskService.class);
        service = new ReportBuilderService(
                mock(NamedParameterJdbcTemplate.class),
                featureAccessService,
                exportTaskService,
                new ObjectMapper());
        operator = new CurrentUser(1L, null, "admin", "管理员", "ADMIN");
    }

    @Test
    void rejectsFieldsOutsideWhitelistBeforeCreatingTask() {
        ReportBuilderService.ReportDefinition definition = new ReportBuilderService.ReportDefinition(
                "危险报表",
                Set.of("studentName", "arbitrarySql"),
                Map.of(),
                Set.of(),
                Set.of(),
                "zh-CN");

        assertThrows(BusinessException.class,
                () -> service.requestExport(definition, "审计需要", operator));

        verify(featureAccessService).require(FeatureCodes.P3_CUSTOM_REPORT_EXPORT);
        verify(exportTaskService, never()).create(anyString(), anyString(), anyString(), eq(operator));
    }

    @Test
    void rejectsFilterAndSortOutsideWhitelist() {
        ReportBuilderService.ReportDefinition definition = new ReportBuilderService.ReportDefinition(
                "历史报表",
                Set.of("batchName"),
                Map.of("rawSql", "select 1"),
                Set.of("studentName"),
                Set.of("participantCount"),
                "zh-CN");

        assertThrows(BusinessException.class,
                () -> service.requestExport(definition, "业务分析", operator));
    }

    @Test
    void rejectsEmptyDefinition() {
        ReportBuilderService.ReportDefinition definition = new ReportBuilderService.ReportDefinition(
                "空报表", Set.of(), Map.of(), Set.of(), Set.of(), "zh-CN");

        assertThrows(BusinessException.class,
                () -> service.requestExport(definition, "业务分析", operator));
    }
}
