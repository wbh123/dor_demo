package com.wust.dormitory.admin;

import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.mapper.AdminDashboardMapper;
import com.wust.dormitory.admin.mapper.BatchCatalogMapper;
import com.wust.dormitory.admin.mapper.BatchPreparationMapper;
import com.wust.dormitory.admin.mapper.MajorManagementMapper;
import com.wust.dormitory.admin.mapper.StudentAdminMapper;
import com.wust.dormitory.admin.model.persistence.AdminDashboardStatsRow;
import com.wust.dormitory.audit.AuditService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDashboardQueryServiceTest {
    @Test
    void dashboardDelegatesAllEightMetricsToTypedMapper() {
        AuditService auditService = mock(AuditService.class);
        AdminCatalogMapper adminCatalogMapper = mock(AdminCatalogMapper.class);
        StudentAdminMapper studentAdminMapper = mock(StudentAdminMapper.class);
        AdminDashboardMapper dashboardMapper = mock(AdminDashboardMapper.class);
        BatchCatalogMapper batchCatalogMapper = mock(BatchCatalogMapper.class);
        ReferenceDataCacheService referenceDataCacheService = mock(ReferenceDataCacheService.class);
        MajorManagementMapper majorManagementMapper = mock(MajorManagementMapper.class);
        BatchPreparationMapper batchPreparationMapper = mock(BatchPreparationMapper.class);
        when(dashboardMapper.findStats()).thenReturn(new AdminDashboardStatsRow(
                6L,
                500L,
                280L,
                220L,
                140L,
                520L,
                310L,
                2L));

        AdminService service = new AdminService(
                auditService,
                adminCatalogMapper,
                studentAdminMapper,
                dashboardMapper,
                batchCatalogMapper,
                referenceDataCacheService,
                majorManagementMapper,
                batchPreparationMapper);
        Map<String, Object> result = service.dashboard();

        verify(dashboardMapper).findStats();
        assertEquals(6L, result.get("majorCount"));
        assertEquals(500L, result.get("studentCount"));
        assertEquals(280L, result.get("maleStudentCount"));
        assertEquals(220L, result.get("femaleStudentCount"));
        assertEquals(140L, result.get("roomCount"));
        assertEquals(520L, result.get("bedCount"));
        assertEquals(310L, result.get("activeAssignmentCount"));
        assertEquals(2L, result.get("openBatchCount"));
    }
}
