package com.wust.dormitory.residency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResidencyServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private CurrentResidencyQueryService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new CurrentResidencyQueryService(jdbc);
    }

    @Test
    void currentUsesStableColumnsAndReturnsAStableEmptyResult() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());

        Map<String, Object> result = service.current(7L);

        assertThat(result).containsEntry("resident", false);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), anyMap());
        assertThat(sql.getValue())
                .contains("db.id AS building_id")
                .contains("ORDER BY ra.assigned_at DESC, ra.id DESC")
                .contains("LIMIT 1")
                .doesNotContain("db.building_id")
                .doesNotContain("db.building_code");
    }

    @Test
    void assignmentNormalizesRoomModeToTheExistingAssignmentResponseShape() {
        Map<String, Object> residency = new LinkedHashMap<>();
        residency.put("residency_id", 9L);
        residency.put("room_number", "301");
        residency.put("building_name", "示例楼栋");
        residency.put("bed_id", null);
        residency.put("bed_confirmed", false);
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(residency));

        Map<String, Object> result = service.assignment(7L);

        assertThat(result).containsEntry("assigned", true);
        assertThat(result.get("assignment")).isInstanceOf(Map.class);
        Map<?, ?> assignment = (Map<?, ?>) result.get("assignment");
        assertThat(assignment.get("room_number")).isEqualTo("301");
        assertThat(assignment.keySet()).contains("bed_id");
        assertThat(assignment.get("bed_id")).isNull();
    }
}
