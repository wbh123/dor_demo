package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomLayoutServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private RoomLayoutService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new RoomLayoutService(jdbc, mock(AuditService.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultFivePersonRoomUsesFourHorizontalUnitsInAStandardTwoByTwoGrid() {
        Map<String, Object> room = Map.of(
                "id", 1L,
                "room_number", "101",
                "room_type", "FIVE_PERSON",
                "capacity", 5,
                "room_version", 0L,
                "state_version", 0L,
                "floor_number", 1,
                "building_name", "示例楼栋");
        List<Map<String, Object>> bedRows = List.of(
                bed(1L, "A", "LOFT_BED_DESK", 1, null),
                bed(2L, "B", "LOFT_BED_DESK", 2, null),
                bed(3L, "C", "LOFT_BED_DESK", 3, null),
                bed(4L, "D-UP", "BUNK_UPPER", 4, 9L),
                bed(5L, "D-LOW", "BUNK_LOWER", 5, 9L));
        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(room))
                .thenReturn(bedRows);

        Map<String, Object> result = service.getLayout(1L);
        List<Map<String, Object>> beds = (List<Map<String, Object>>) result.get("beds");

        assertThat(beds).hasSize(5);
        assertPlacement(beds.get(0), -2.35, -1.65, 0);
        assertPlacement(beds.get(1), 2.35, -1.65, 0);
        assertPlacement(beds.get(2), -2.35, 1.65, 0);
        assertPlacement(beds.get(3), 2.35, 1.65, 0);
        assertPlacement(beds.get(4), 2.35, 1.65, 0);
        assertThat(result).containsEntry("layout_source", RoomLayoutService.DEFAULT_LAYOUT);
    }

    private Map<String, Object> bed(
            long id,
            String code,
            String type,
            int position,
            Long frameId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("bed_code", code);
        row.put("bed_type", type);
        row.put("position_index", position);
        row.put("bed_frame_id", frameId);
        row.put("operational_status", "ENABLED");
        row.put("occupied", 0);
        row.put("layout_x", null);
        row.put("layout_z", null);
        row.put("rotation_degrees", null);
        row.put("custom_layout", 0);
        return row;
    }

    private void assertPlacement(
            Map<String, Object> bed,
            double x,
            double z,
            int rotation) {
        assertThat(((Number) bed.get("layout_x")).doubleValue()).isEqualTo(x);
        assertThat(((Number) bed.get("layout_z")).doubleValue()).isEqualTo(z);
        assertThat(((Number) bed.get("rotation_degrees")).intValue()).isEqualTo(rotation);
    }
}
