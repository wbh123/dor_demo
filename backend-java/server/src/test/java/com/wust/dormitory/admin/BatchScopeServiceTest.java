package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchScopeServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private AuditService auditService;
    private BatchScopeService service;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        auditService = mock(AuditService.class);
        service = new BatchScopeService(jdbc, auditService);
        operator = new CurrentUser(7L, null, "admin", "管理员", "ADMIN");
    }

    @Test
    void loadReturnsSelectableCandidatesAndCurrentCounts() {
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", 12L,
                        "batch_status", "DRAFT",
                        "batch_name", "2026级选寝")))
                .thenReturn(List.of(
                        Map.of("id", 1L, "student_number", "202600000001", "selected", 1),
                        Map.of("id", 2L, "student_number", "202600000002", "selected", 0)))
                .thenReturn(List.of(
                        Map.of("id", 31L, "room_number", "301", "selected", 1, "selectable", 1),
                        Map.of("id", 32L, "room_number", "302", "selected", 0, "selectable", 1)));

        Map<String, Object> result = service.get(12L);

        assertThat(result)
                .containsEntry("batchId", 12L)
                .containsEntry("batchStatus", "DRAFT")
                .containsEntry("selectedStudentCount", 1)
                .containsEntry("selectedRoomCount", 1);
        assertThat((List<?>) result.get("students")).hasSize(2);
        assertThat((List<?>) result.get("rooms")).hasSize(2);
    }

    @Test
    void updateReplacesBroadBuildingScopeWithExactStudentAndRoomScope() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "id", 12L,
                "batch_status", "DRAFT",
                "batch_name", "2026级选寝")));
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class)))
                .thenReturn(2)
                .thenReturn(1);

        Map<String, Object> result = service.update(
                12L,
                new BatchScopeService.UpdateCommand(List.of(1L, 2L, 2L), List.of(31L, 31L)),
                operator);

        assertThat(result)
                .containsEntry("batchId", 12L)
                .containsEntry("selectedStudentCount", 2)
                .containsEntry("selectedRoomCount", 1);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(6)).update(sql.capture(), anyMap());
        String allSql = String.join("\n", sql.getAllValues());
        assertThat(allSql)
                .contains("DELETE FROM batch_bed_scope")
                .contains("DELETE FROM batch_room_scope")
                .contains("DELETE FROM batch_building_scope")
                .contains("DELETE FROM batch_student_eligibility")
                .contains("INSERT INTO batch_student_eligibility")
                .contains("INSERT INTO batch_room_scope");
        verify(auditService).success(
                eq(operator),
                eq("BATCH_SCOPE_UPDATE"),
                eq("SELECTION_BATCH"),
                eq(12L),
                eq("配置批次学生和宿舍范围"),
                eq(null),
                eq(result));
    }

    @Test
    void updateRejectsPublishedBatch() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "id", 12L,
                "batch_status", "PUBLISHED",
                "batch_name", "2026级选寝")));

        assertThatThrownBy(() -> service.update(
                12L,
                new BatchScopeService.UpdateCommand(List.of(1L), List.of(31L)),
                operator))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("BATCH_SCOPE_LOCKED");
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });

        verify(jdbc, never()).update(anyString(), anyMap());
    }

    @Test
    void requireReadyReportsMissingStudentScopeBeforeRoomScope() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class)))
                .thenReturn(0)
                .thenReturn(0);

        assertThatThrownBy(() -> service.requireReady(12L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("BATCH_STUDENT_SCOPE_REQUIRED"));
    }

    @Test
    void requireReadyReportsMissingRoomScope() {
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class)))
                .thenReturn(2)
                .thenReturn(0);

        assertThatThrownBy(() -> service.requireReady(12L))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("BATCH_ROOM_SCOPE_REQUIRED"));
    }
}
