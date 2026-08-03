package com.wust.dormitory.residency;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedSelectionEligibilityGuardTest {
    private NamedParameterJdbcTemplate jdbc;
    private ResidencyPolicyService policy;
    private BedSelectionEligibilityGuard guard;
    private CurrentUser leader;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        policy = mock(ResidencyPolicyService.class);
        guard = new BedSelectionEligibilityGuard(jdbc, policy);
        leader = new CurrentUser(1L, 10L, "202600000001", "队长", "STUDENT");
    }

    @Test
    void personalBedSelectionStopsImmediatelyForRoomMode() {
        when(policy.batch(20L)).thenReturn(Map.of("selection_mode", "ROOM"));

        assertThatThrownBy(() -> guard.requirePersonalAllowed(20L, 10L, 100L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("BATCH_SELECTION_MODE_MISMATCH");
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });

        verify(policy, never()).requireBatchEligibility(anyLong(), anyLong());
    }

    @Test
    void personalBedSelectionRunsTheCompleteEligibilityChain() {
        Map<String, Object> batch = Map.of(
                "selection_mode", "BED",
                "separate_student_categories", 1);
        Map<String, Object> room = Map.of(
                "id", 30L,
                "resident_scope", "DOMESTIC",
                "gender_restriction", "FEMALE");
        Map<String, Object> student = Map.of(
                "id", 10L,
                "student_category", "DOMESTIC",
                "gender", "FEMALE");
        when(policy.batch(20L)).thenReturn(batch);
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(room));
        when(policy.student(10L)).thenReturn(student);
        when(policy.unknownBedResidentCount(30L)).thenReturn(0);

        assertThatCode(() -> guard.requirePersonalAllowed(20L, 10L, 100L))
                .doesNotThrowAnyException();

        verify(policy).requireBatchEligibility(20L, 10L);
        verify(policy).requireBedInBatch(20L, 100L);
        verify(policy).requireRoomLockedByBatch(20L, 30L);
        verify(policy).requireStudentEligibleForRoom(student, batch, room);
        verify(policy).requireNoActiveResidency(10L);
    }

    @Test
    void teamBedSelectionRequiresAtLeastOneBed() {
        assertThatThrownBy(() -> guard.requireTeamAllowed(20L, 30L, List.of(), leader))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("TEAM_BEDS_REQUIRED"));

        verify(policy, never()).batch(anyLong());
    }

    @Test
    void teamBedSelectionRejectsBedCountDifferentFromLockedMemberCount() {
        when(policy.batch(20L)).thenReturn(Map.of(
                "selection_mode", "BED",
                "separate_student_categories", 1));
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", 30L,
                        "batch_id", 20L,
                        "leader_student_id", 10L,
                        "team_status", "LOCKED",
                        "member_count", 2)))
                .thenReturn(List.of(
                        Map.of("student_id", 10L, "gender", "FEMALE", "student_category", "DOMESTIC"),
                        Map.of("student_id", 11L, "gender", "FEMALE", "student_category", "DOMESTIC")));

        assertThatThrownBy(() -> guard.requireTeamAllowed(20L, 30L, List.of(100L), leader))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("TEAM_BED_COUNT_MISMATCH");
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });
    }
}
