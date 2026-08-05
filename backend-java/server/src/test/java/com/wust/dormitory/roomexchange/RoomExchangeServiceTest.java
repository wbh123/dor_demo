package com.wust.dormitory.roomexchange;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.residency.ResidencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomExchangeServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private ResidencyPolicyService residencyPolicy;
    private RoomExchangeService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        residencyPolicy = mock(ResidencyPolicyService.class);
        service = new RoomExchangeService(
                jdbc,
                residencyPolicy,
                mock(ResidencyService.class),
                mock(AuditService.class));
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of("MUTUAL_CONFIRMATION"));
    }

    @Test
    void candidateLookupRequiresAStudentNumber() {
        assertThatThrownBy(() -> service.candidates(7L, "  "))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("ROOM_EXCHANGE_STUDENT_NUMBER_INVALID"));
    }

    @Test
    void candidateLookupSearchesOnlyStudentNumberAndLimitsResults() {
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("room_id", 11L)));
        when(residencyPolicy.student(7L)).thenReturn(Map.of(
                "gender", "F",
                "student_category", "DOMESTIC"));
        when(residencyPolicy.room(11L, false)).thenReturn(Map.of(
                "operational_status", "ENABLED",
                "gender_restriction", "F",
                "resident_scope", "ALL"));
        when(residencyPolicy.student(8L)).thenReturn(Map.of(
                "gender", "F",
                "student_category", "DOMESTIC"));
        when(residencyPolicy.room(12L, false)).thenReturn(Map.of(
                "operational_status", "ENABLED",
                "gender_restriction", "F",
                "resident_scope", "ALL"));
        when(residencyPolicy.roomAllowsCategory("ALL", "DOMESTIC", false))
                .thenReturn(true);
        when(jdbc.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of(Map.of(
                        "target_student_id", 8L,
                        "student_number", "20260008",
                        "room_id", 12L)));

        List<Map<String, Object>> result = service.candidates(7L, "2026%_");

        assertThat(result).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> parameters =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).queryForList(sql.capture(), parameters.capture());
        assertThat(sql.getValue())
                .contains("target.student_number LIKE :studentNumber")
                .contains("assignment.assignment_status='ACTIVE'")
                .contains("participant_lock.student_id IS NULL")
                .contains("LIMIT 20")
                .doesNotContain("target.student_name LIKE");
        assertThat(parameters.getValue().getValue("studentNumber"))
                .isEqualTo("%2026\\%\\_%");
    }
}
