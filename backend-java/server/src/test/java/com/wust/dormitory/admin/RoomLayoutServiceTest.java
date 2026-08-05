package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.wust.dormitory.security.CurrentUser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.argThat;

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


    @Test
    @SuppressWarnings("unchecked")
    void emptyBunkCanCollapseToSingleBed() {
        Map<String, Object> lockedRoom = Map.of(
                "id", 1L, "version", 4L, "state_version", 4L, "capacity", 2);
        Map<String, Object> roomBefore = Map.of(
                "id", 1L, "room_number", "101", "room_type", "OTHER",
                "capacity", 2, "room_version", 4L, "state_version", 4L,
                "floor_number", 1, "building_name", "示例楼栋");
        Map<String, Object> roomAfter = Map.of(
                "id", 1L, "room_number", "101", "room_type", "OTHER",
                "capacity", 1, "room_version", 5L, "state_version", 5L,
                "floor_number", 1, "building_name", "示例楼栋");
        List<Map<String, Object>> lockedBeds = List.of(
                lockedBed(1L, "A-UP", "BUNK_UPPER", 1, 9L, 0),
                lockedBed(2L, "A-LOW", "BUNK_LOWER", 2, 9L, 0));
        List<Map<String, Object>> beforeBeds = List.of(
                layoutBed(1L, "A-UP", "BUNK_UPPER", 1, 9L, 0),
                layoutBed(2L, "A-LOW", "BUNK_LOWER", 2, 9L, 0));
        List<Map<String, Object>> afterBeds = List.of(
                layoutBed(1L, "A-UP", "SINGLE_BED", 1, null, 0));

        when(jdbc.queryForList(anyString(), anyMap()))
                .thenReturn(List.of(lockedRoom))
                .thenReturn(lockedBeds)
                .thenReturn(List.of(roomBefore))
                .thenReturn(beforeBeds)
                .thenReturn(List.of(roomAfter))
                .thenReturn(afterBeds);
        when(jdbc.update(anyString(), anyMap())).thenReturn(1);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);

        Map<String, Object> result = service.updateLayout(
                1L,
                new RoomLayoutService.LayoutCommand(
                        4L,
                        "空上下铺调整为单人床",
                        List.of(new RoomLayoutService.LayoutItem(1L, "SINGLE_BED", 1.5, 1.0, 90))),
                new CurrentUser(10L, null, "admin", "管理员", "ADMIN"));

        assertThat(((Map<String, Object>) result.get("room")).get("capacity")).isEqualTo(1);
        verify(jdbc).update(argThat(sql -> sql.contains("operational_status='RETIRED'")), anyMap());
        verify(jdbc).update(argThat(sql -> sql.contains("SET bed_frame_id=NULL, bed_type=:bedType")), any(MapSqlParameterSource.class));
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


    private Map<String, Object> lockedBed(long id, String code, String type, int position, Long frameId, int occupied) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id); row.put("bed_code", code); row.put("bed_type", type);
        row.put("position_index", position); row.put("bed_frame_id", frameId); row.put("occupied", occupied);
        return row;
    }

    private Map<String, Object> layoutBed(long id, String code, String type, int position, Long frameId, int occupied) {
        Map<String, Object> row = lockedBed(id, code, type, position, frameId, occupied);
        row.put("operational_status", "ENABLED"); row.put("layout_x", 1.5); row.put("layout_z", 1.0);
        row.put("rotation_degrees", 90); row.put("custom_layout", 1);
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
