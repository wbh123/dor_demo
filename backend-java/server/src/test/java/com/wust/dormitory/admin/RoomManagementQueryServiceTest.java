package com.wust.dormitory.admin;

import com.wust.dormitory.admin.mapper.RoomCatalogMapper;
import com.wust.dormitory.admin.model.persistence.RoomCatalogRow;
import com.wust.dormitory.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomManagementQueryServiceTest {
    @Test
    void roomListUsesTypedMapperAndPreservesExistingResponseKeys() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AuditService auditService = mock(AuditService.class);
        RoomCatalogMapper mapper = mock(RoomCatalogMapper.class);
        RoomCatalogRow row = new RoomCatalogRow(
                11L,
                2L,
                "北苑一栋",
                3,
                "305",
                "QUAD",
                4,
                "F",
                "GRADUATE_ONLY",
                "DOMESTIC_ONLY",
                "F",
                "MIXED",
                "MIXED",
                "ENABLED",
                7L,
                null,
                4L,
                3L,
                1L,
                0L,
                2L,
                1L,
                1L,
                2L);
        when(mapper.findRooms(2L, "F")).thenReturn(List.of(row));

        RoomManagementService service =
                new RoomManagementService(jdbc, auditService, mapper);
        List<Map<String, Object>> result = service.rooms(2L, "F");

        verify(mapper).findRooms(2L, "F");
        assertEquals(1, result.size());
        Map<String, Object> room = result.getFirst();
        assertEquals(11L, room.get("id"));
        assertEquals("北苑一栋", room.get("building_name"));
        assertEquals("GRADUATE_ONLY", room.get("education_level_scope"));
        assertEquals("DOMESTIC_ONLY", room.get("resident_scope"));
        assertEquals("F", room.get("building_gender_restriction"));
        assertEquals("MIXED", room.get("building_education_level_scope"));
        assertEquals("MIXED", room.get("building_resident_scope"));
        assertEquals(2L, room.get("active_resident_count"));
        assertEquals(2L, room.get("remaining_capacity"));
        assertNull(room.get("remark"));
    }
}
