package com.wust.dormitory.selection;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BedScopeGuardTest {
    @Test
    void filtersImmutableRoomSnapshotWithoutMutatingInput() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForObject(
                anyString(),
                any(SqlParameterSource.class),
                eq(Integer.class)
        )).thenReturn(1);
        BedScopeGuard guard = new BedScopeGuard(jdbc);

        Map<String, Object> snapshot = Map.of(
                "room", Map.of("id", 1L),
                "beds", List.of(Map.of("id", 11L), Map.of("id", 12L))
        );

        Map<String, Object> filtered = guard.filterRoomSnapshot(1L, snapshot);

        assertThat(filtered).isNotSameAs(snapshot);
        assertThat((List<?>) filtered.get("beds")).hasSize(2);
        assertThat((List<?>) snapshot.get("beds")).hasSize(2);
    }
}
