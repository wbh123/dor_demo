package com.wust.dormitory.roomexchange;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.roomexchange.mapper.RoomExchangeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomExchangeServiceTest {
    private RoomExchangeMapper mapper;
    private ResidencyPolicyService residencyPolicy;
    private RoomExchangeService service;

    @BeforeEach
    void setUp() {
        mapper = mock(RoomExchangeMapper.class);
        residencyPolicy = mock(ResidencyPolicyService.class);
        RoomExchangeWorkflowSupport workflow =
                new RoomExchangeWorkflowSupport(mapper, residencyPolicy);
        service = new RoomExchangeService(
                mapper,
                mock(ResidencyService.class),
                mock(AuditService.class),
                workflow);
        when(mapper.findPolicyMode()).thenReturn("MUTUAL_CONFIRMATION");
    }

    @Test
    void candidateLookupRequiresAStudentNumber() {
        assertThatThrownBy(() -> service.candidates(7L, "  "))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("ROOM_EXCHANGE_STUDENT_NUMBER_INVALID"));
    }

    @Test
    void candidateLookupUsesOneCompatibleSetQueryAndEscapesLikePattern() {
        when(mapper.findActiveResidency(7L)).thenReturn(List.of(Map.of(
                "id", 101L,
                "student_id", 7L,
                "room_id", 11L)));
        when(residencyPolicy.student(7L)).thenReturn(Map.of(
                "gender", "F",
                "student_category", "DOMESTIC"));
        when(residencyPolicy.room(11L, false)).thenReturn(Map.of(
                "operational_status", "ENABLED",
                "gender_restriction", "F",
                "resident_scope", "MIXED"));
        List<Map<String, Object>> expected = List.of(Map.of(
                "target_student_id", 8L,
                "student_number", "20260008",
                "room_id", 12L));
        when(mapper.findCompatibleCandidates(
                7L, "F", "DOMESTIC", "F", "MIXED", "ENABLED", "%2026\\%\\_%"))
                .thenReturn(expected);

        List<Map<String, Object>> result = service.candidates(7L, "2026%_");

        assertThat(result).isEqualTo(expected);
        verify(mapper).findCompatibleCandidates(
                7L, "F", "DOMESTIC", "F", "MIXED", "ENABLED", "%2026\\%\\_%");
        verify(residencyPolicy, never()).student(8L);
        verify(residencyPolicy, never()).room(12L, false);
    }
}
