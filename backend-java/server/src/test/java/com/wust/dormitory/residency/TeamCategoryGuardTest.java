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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamCategoryGuardTest {
    private NamedParameterJdbcTemplate jdbc;
    private TeamCategoryGuard guard;
    private CurrentUser leader;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        guard = new TeamCategoryGuard(jdbc);
        leader = new CurrentUser(1L, 10L, "202600000001", "队长", "STUDENT");
    }

    @Test
    void invitationAllowsTheSameCategoryWhenSeparationIsEnabled() {
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of(
                        "batch_id", 20L,
                        "separate_student_categories", 1,
                        "student_category", "DOMESTIC")))
                .thenReturn(List.of(Map.of(
                        "id", 11L,
                        "student_category", "DOMESTIC")));

        assertThatCode(() -> guard.requireInvitationAllowed("202600000002", leader))
                .doesNotThrowAnyException();
    }

    @Test
    void invitationRejectsMixedCategoriesWhenSeparationIsEnabled() {
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of(
                        "batch_id", 20L,
                        "separate_student_categories", 1,
                        "student_category", "DOMESTIC")))
                .thenReturn(List.of(Map.of(
                        "id", 11L,
                        "student_category", "INTERNATIONAL")));

        assertThatThrownBy(() -> guard.requireInvitationAllowed("202600000002", leader))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("TEAM_STUDENT_CATEGORY_MISMATCH");
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });
    }

    @Test
    void lockSkipsCategoryAggregationWhenTheBatchAllowsMixing() {
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", 30L,
                        "batch_id", 20L,
                        "leader_student_id", 10L,
                        "separate_student_categories", 0)));

        assertThatCode(() -> guard.requireLockAllowed(30L, leader))
                .doesNotThrowAnyException();

        verify(jdbc, never()).queryForObject(anyString(), anyMap(), eq(Integer.class));
    }

    @Test
    void lockRejectsAnExistingMixedCategoryTeamWhenSeparationIsEnabled() {
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(Map.of(
                        "id", 30L,
                        "batch_id", 20L,
                        "leader_student_id", 10L,
                        "separate_student_categories", 1)));
        when(jdbc.queryForObject(anyString(), anyMap(), eq(Integer.class))).thenReturn(2);

        assertThatThrownBy(() -> guard.requireLockAllowed(30L, leader))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("TEAM_STUDENT_CATEGORY_MISMATCH"));
    }
}
