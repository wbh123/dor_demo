package com.wust.dormitory.residency;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.mapper.ResidencyMapper;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResidencyMutationServiceTest {
    private ResidencyMapper mapper;
    private AuditService audit;
    private ResidencyHistoryWriter historyWriter;
    private ResidencyService service;
    private CurrentUser operator;

    @BeforeEach
    void setUp() {
        mapper = mock(ResidencyMapper.class);
        audit = mock(AuditService.class);
        historyWriter = mock(ResidencyHistoryWriter.class);
        service = new ResidencyService(
                mapper,
                mock(ResidencyPolicyService.class),
                audit,
                historyWriter);
        operator = new CurrentUser(9L, null, "admin", "管理员", "ADMIN");
    }

    @Test
    void confirmBedRejectsBedFromAnotherRoom() {
        when(mapper.lockResidency(100L)).thenReturn(Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "bed_id", 1L, "assignment_status", "ACTIVE"));
        when(mapper.lockBed(2L)).thenReturn(Map.of(
                "id", 2L, "room_id", 11L, "operational_status", "ENABLED"));

        assertThatThrownBy(() -> service.confirmBed(100L, 2L, "核查实际床位", operator))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("BED_NOT_IN_RESIDENCY_ROOM"));
        verify(mapper, never()).confirmBed(100L, 2L);
    }

    @Test
    void confirmBedRejectsBedOccupiedByAnotherActiveResidency() {
        when(mapper.lockResidency(100L)).thenReturn(Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "bed_id", 1L, "assignment_status", "ACTIVE"));
        when(mapper.lockBed(2L)).thenReturn(Map.of(
                "id", 2L, "room_id", 10L, "operational_status", "ENABLED"));
        when(mapper.countOtherActiveBedOccupants(2L, 100L)).thenReturn(1);

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
        when(mapper.lockResidency(100L)).thenReturn(before);
        when(mapper.lockBed(2L)).thenReturn(Map.of(
                "id", 2L, "room_id", 10L, "operational_status", "ENABLED"));
        when(mapper.countOtherActiveBedOccupants(2L, 100L)).thenReturn(0);
        when(mapper.findResidency(100L)).thenReturn(after);

        assertThat(service.confirmBed(100L, 2L, "核查实际床位", operator))
                .isEqualTo(after);
        verify(mapper, times(1)).findResidency(100L);
        verify(historyWriter).append(eq(100L), eq(7L), eq(10L), eq(2L),
                eq("BED_CHANGED"), eq(9L), eq("核查实际床位"), eq(before), eq(after));
        verify(audit).success(eq(operator), eq("RESIDENCY_BED_CONFIRM"),
                eq("ROOM_ASSIGNMENT"), eq(100L), eq("核查实际床位"),
                eq(before), eq(after));
    }

    @Test
    void confirmingSameBedIsIdempotent() {
        Map<String, Object> before = Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "bed_id", 2L, "assignment_status", "ACTIVE");
        when(mapper.lockResidency(100L)).thenReturn(before);
        when(mapper.findResidency(100L)).thenReturn(before);

        assertThat(service.confirmBed(100L, 2L, "重复核查", operator)).isEqualTo(before);
        verify(mapper, never()).lockBed(2L);
        verify(mapper, never()).countOtherActiveBedOccupants(2L, 100L);
        verifyNoInteractions(historyWriter);
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
        when(mapper.lockResidency(100L)).thenReturn(before);
        when(mapper.findResidency(100L)).thenReturn(after);

        assertThat(service.end(100L, "毕业退宿", operator)).isEqualTo(after);
        verify(mapper, times(1)).findResidency(100L);
        verify(historyWriter).append(eq(100L), eq(7L), eq(10L), eq(2L),
                eq("RESIDENCY_ENDED"), eq(9L), eq("毕业退宿"), eq(before), eq(after));
        verify(audit).success(eq(operator), eq("RESIDENCY_END"),
                eq("ROOM_ASSIGNMENT"), eq(100L), eq("毕业退宿"),
                eq(before), eq(after));
    }

    @Test
    void repeatedEndReturnsClearConflict() {
        when(mapper.lockResidency(100L)).thenReturn(Map.of(
                "id", 100L, "student_id", 7L, "room_id", 10L,
                "assignment_status", "ENDED"));

        assertThatThrownBy(() -> service.end(100L, "再次退宿", operator))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("RESIDENCY_NOT_ACTIVE"));
    }
}
