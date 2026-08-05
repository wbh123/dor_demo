package com.wust.dormitory.residency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResidencyMutationServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private AuditService audit;
    private ResidencyService service;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        audit = mock(AuditService.class);
        service = new ResidencyService(
                jdbc,
                mock(ResidencyPolicyService.class),
                audit,
                new ObjectMapper());
        operator = new CurrentUser(9L, null, "admin", "管理员", "ADMIN");
    }

    @Test
    void confirmBedRejectsBedFromAnotherRoom() {
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", 100L, "student_id", 7L, "room_id", 10L,
                        "bed_id", 1L, "assignment_status", "ACTIVE")))
                .thenReturn(List.of(Map.of(
                        "id", 2L, "room_id", 11L, "operational_status", "ENABLED")));

        assertThatThrownBy(() -> service.confirmBed(100L, 2L, "核查实际床位", operator))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("BED_NOT_IN_RESIDENCY_ROOM"));
        verify(jdbc, never()).update(anyString(), anyMap());
    }

    @Test
    void confirmBedRejectsBedOccupiedByAnotherActiveResidency() {
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", 100L, "student_id", 7L, "room_id", 10L,
                        "bed_id", 1L, "assignment_status", "ACTIVE")))
                .thenReturn(List.of(Map.of(
                        "id", 2L, "room_id", 10L, "operational_status", "ENABLED")));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(1);

        assertThatThrownBy(() -> service.confirmBed(100L, 2L, "核查实际床位", operator))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("BED_ALREADY_OCCUPIED"));
    }

    @Test
    void confirmBedUpdatesAndReadsResultOnlyOnce() {
        Map<String, Object> before = Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "bed_id", 1L, "assignment_status", "ACTIVE");
        Map<String, Object> after = Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "bed_id", 2L, "assignment_status", "ACTIVE");
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(before))
                .thenReturn(List.of(Map.of(
                        "id", 2L, "room_id", 10L, "operational_status", "ENABLED")));
        when(jdbc.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(0);
        when(jdbc.queryForMap(anyString(), anyMap())).thenReturn(after);

        assertThat(service.confirmBed(100L, 2L, "核查实际床位", operator))
                .isEqualTo(after);
        verify(jdbc, times(1)).queryForMap(anyString(), anyMap());
        verify(audit).success(eq(operator), eq("RESIDENCY_BED_CONFIRM"),
                eq("ROOM_ASSIGNMENT"), eq(100L), eq("核查实际床位"),
                eq(before), eq(after));
    }

    @Test
    void confirmingSameBedIsIdempotent() {
        Map<String, Object> before = Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "bed_id", 2L, "assignment_status", "ACTIVE");
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(before));
        when(jdbc.queryForMap(anyString(), anyMap())).thenReturn(before);

        assertThat(service.confirmBed(100L, 2L, "重复核查", operator)).isEqualTo(before);
        verify(jdbc, never()).queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class));
        verify(audit, never()).success(any(), anyString(), anyString(), any(), anyString(), any(), any());
    }

    @Test
    void endReturnsEndedRecordAndUsesItForAudit() {
        Map<String, Object> before = Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "bed_id", 2L, "assignment_status", "ACTIVE");
        Map<String, Object> after = Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "bed_id", 2L, "assignment_status", "ENDED",
                "end_reason", "毕业退宿");
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(before));
        when(jdbc.queryForMap(anyString(), anyMap())).thenReturn(after);

        assertThat(service.end(100L, "毕业退宿", operator)).isEqualTo(after);
        verify(jdbc, times(1)).queryForMap(anyString(), anyMap());
        verify(audit).success(eq(operator), eq("RESIDENCY_END"),
                eq("ROOM_ASSIGNMENT"), eq(100L), eq("毕业退宿"),
                eq(before), eq(after));
    }

    @Test
    void repeatedEndReturnsClearConflict() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "assignment_status", "ENDED")));

        assertThatThrownBy(() -> service.end(100L, "再次退宿", operator))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("RESIDENCY_NOT_ACTIVE"));
    }
}
