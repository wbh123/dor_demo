package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchCreationServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private BatchRuleTemplateService ruleTemplateService;
    private FeatureAccessService featureAccessService;
    private AuditService auditService;
    private BatchCreationService service;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        ruleTemplateService = mock(BatchRuleTemplateService.class);
        featureAccessService = mock(FeatureAccessService.class);
        auditService = mock(AuditService.class);
        service = new BatchCreationService(jdbc, ruleTemplateService, featureAccessService, auditService);
        operator = new CurrentUser(7L, null, "admin", "管理员", "ADMIN");
    }

    @Test
    void createBindsTheFixedBuiltinQuestionnaireAndReturnsItsIdentity() {
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("id", 11L)))
                .thenReturn(List.of(Map.of("id", 22L)));
        when(ruleTemplateService.resolveForBatch(null)).thenReturn(snapshot());
        doAnswer(invocation -> {
            GeneratedKeyHolder keyHolder = invocation.getArgument(2);
            keyHolder.getKeyList().add(Map.of("id", 501L));
            return 1;
        }).when(jdbc).update(anyString(), any(), any(GeneratedKeyHolder.class), any(String[].class));

        Map<String, Object> result = service.create(command("ROOM"), operator);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(2)).queryForList(sql.capture(), anyMap());
        assertThat(sql.getAllValues().getFirst())
                .contains("FROM questionnaire_version")
                .contains("version_code='SYSTEM-PREFERENCE-V1'")
                .doesNotContain("version_status='PUBLISHED'")
                .doesNotContain("ORDER BY published_at");
        assertThat(result)
                .containsEntry("id", 501L)
                .containsEntry("questionnaireType", "BUILTIN_FIXED")
                .containsEntry("questionnaireCode", "SYSTEM-PREFERENCE-V1");
        verify(auditService).success(
                eq(operator),
                eq("BATCH_CREATE"),
                eq("SELECTION_BATCH"),
                eq(501L),
                eq("使用系统内置固定问卷和批次规则模板创建草稿批次"),
                eq(null),
                eq(result));
    }

    @Test
    void createFailsWithStablePublicErrorWhenBuiltinQuestionnaireIsMissing() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(command("ROOM"), operator))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("BUILTIN_QUESTIONNAIRE_MISSING");
                    assertThat(exception.getMessage()).isEqualTo("系统内置个人偏好问卷不可用");
                    assertThat(exception.getMessage()).doesNotContain("数据库");
                });

        verify(ruleTemplateService, never()).resolveForBatch(any());
    }

    @Test
    void invalidTimeIsRejectedBeforeAnyRepositoryAccess() {
        BatchCreationService.CreateCommand invalid = new BatchCreationService.CreateCommand(
                "2026-A",
                "2026级选寝",
                LocalDateTime.of(2026, 9, 1, 12, 0),
                LocalDateTime.of(2026, 9, 1, 12, 0),
                null,
                "ROOM",
                true);

        assertThatThrownBy(() -> service.create(invalid, operator))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("BATCH_TIME_INVALID"));

        verify(jdbc, never()).queryForList(anyString(), anyMap());
    }

    private BatchCreationService.CreateCommand command(String mode) {
        return new BatchCreationService.CreateCommand(
                "2026-A",
                "2026级选寝",
                LocalDateTime.of(2026, 9, 1, 8, 0),
                LocalDateTime.of(2026, 9, 2, 20, 0),
                null,
                mode,
                true);
    }

    private BatchRuleTemplateService.RuleSnapshot snapshot() {
        return new BatchRuleTemplateService.RuleSnapshot(
                31L,
                "DEFAULT",
                2,
                120,
                2,
                true,
                2,
                5,
                true,
                "ADMIN_ALLOCATION",
                "RULE-V2");
    }
}
