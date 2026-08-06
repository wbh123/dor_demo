package com.wust.dormitory.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.mapper.AdminDashboardMapper;
import com.wust.dormitory.admin.mapper.BatchCatalogMapper;
import com.wust.dormitory.admin.mapper.StudentAdminMapper;
import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;
import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;
import com.wust.dormitory.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminCatalogQueryServiceTest {
    @Test
    void majorAndBuildingListsDelegateToTypedMapperAndPreserveResponseKeys() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AuditService auditService = mock(AuditService.class);
        AdminCatalogMapper mapper = mock(AdminCatalogMapper.class);
        StudentAdminMapper studentAdminMapper = mock(StudentAdminMapper.class);
        AdminDashboardMapper dashboardMapper = mock(AdminDashboardMapper.class);
        BatchCatalogMapper batchCatalogMapper = mock(BatchCatalogMapper.class);
        LocalDateTime now = LocalDateTime.of(2026, 8, 6, 10, 0);

        when(mapper.findMajors(true)).thenReturn(List.of(new MajorCatalogRow(
                1L, "CS", "计算机科学与技术", true, now, now)));
        when(mapper.findBuildings()).thenReturn(List.of(new BuildingCatalogRow(
                2L, "F01", "示例一栋", "F", "MIXED", "MIXED",
                true, "示例校区", 12L, 47L)));

        AdminService service = new AdminService(
                jdbc,
                objectMapper,
                auditService,
                mapper,
                studentAdminMapper,
                dashboardMapper,
                batchCatalogMapper);

        List<Map<String, Object>> majors = service.majors(true);
        List<Map<String, Object>> buildings = service.buildings();

        verify(mapper).findMajors(true);
        verify(mapper).findBuildings();
        assertEquals("CS", majors.getFirst().get("major_code"));
        assertEquals("计算机科学与技术", majors.getFirst().get("major_name"));
        assertEquals(true, majors.getFirst().get("enabled"));
        assertEquals("示例校区", buildings.getFirst().get("campus_name"));
        assertEquals("MIXED", buildings.getFirst().get("education_level_scope"));
        assertEquals(12L, buildings.getFirst().get("room_count"));
        assertEquals(47L, buildings.getFirst().get("bed_count"));
    }
}
