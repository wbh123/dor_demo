package com.wust.dormitory.admin;

import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.mapper.AdminDashboardMapper;
import com.wust.dormitory.admin.mapper.BatchCatalogMapper;
import com.wust.dormitory.admin.mapper.BatchPreparationMapper;
import com.wust.dormitory.admin.mapper.MajorManagementMapper;
import com.wust.dormitory.admin.mapper.StudentAdminMapper;
import com.wust.dormitory.admin.model.persistence.StudentCatalogRow;
import com.wust.dormitory.admin.model.query.StudentCatalogQuery;
import com.wust.dormitory.audit.AuditService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentAdminQueryServiceTest {
    @Test
    void studentListNormalizesPagingAndDelegatesFiltersToTypedMapper() {
        AuditService auditService = mock(AuditService.class);
        AdminCatalogMapper adminCatalogMapper = mock(AdminCatalogMapper.class);
        StudentAdminMapper studentAdminMapper = mock(StudentAdminMapper.class);
        AdminDashboardMapper dashboardMapper = mock(AdminDashboardMapper.class);
        BatchCatalogMapper batchCatalogMapper = mock(BatchCatalogMapper.class);
        ReferenceDataCacheService referenceDataCacheService = mock(ReferenceDataCacheService.class);
        MajorManagementMapper majorManagementMapper = mock(MajorManagementMapper.class);
        BatchPreparationMapper batchPreparationMapper = mock(BatchPreparationMapper.class);
        StudentCatalogQuery expectedQuery = new StudentCatalogQuery(
                "%2026%",
                "F",
                9L,
                200,
                0);
        when(studentAdminMapper.countStudents(expectedQuery)).thenReturn(1L);
        when(studentAdminMapper.findStudents(expectedQuery)).thenReturn(List.of(
                new StudentCatalogRow(
                        18L,
                        "20260018",
                        "示例学生",
                        "F",
                        9L,
                        "CS",
                        "计算机科学与技术",
                        "ACTIVE")));

        AdminService service = new AdminService(
                auditService,
                adminCatalogMapper,
                studentAdminMapper,
                dashboardMapper,
                batchCatalogMapper,
                referenceDataCacheService,
                majorManagementMapper,
                batchPreparationMapper);
        Map<String, Object> result = service.students(" 2026 ", "F", 9L, 0, 500);

        verify(studentAdminMapper).countStudents(expectedQuery);
        verify(studentAdminMapper).findStudents(expectedQuery);
        assertEquals(1, result.get("page"));
        assertEquals(200, result.get("size"));
        assertEquals(1, result.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) result.get("items");
        assertEquals(1, items.size());
        assertEquals("20260018", items.getFirst().get("student_number"));
        assertEquals("计算机科学与技术", items.getFirst().get("major_name"));
        assertEquals("ACTIVE", items.getFirst().get("account_status"));
    }

    @Test
    void blankFiltersRemainAbsentAndOffsetUsesNormalizedPage() {
        AuditService auditService = mock(AuditService.class);
        AdminCatalogMapper adminCatalogMapper = mock(AdminCatalogMapper.class);
        StudentAdminMapper studentAdminMapper = mock(StudentAdminMapper.class);
        AdminDashboardMapper dashboardMapper = mock(AdminDashboardMapper.class);
        BatchCatalogMapper batchCatalogMapper = mock(BatchCatalogMapper.class);
        ReferenceDataCacheService referenceDataCacheService = mock(ReferenceDataCacheService.class);
        MajorManagementMapper majorManagementMapper = mock(MajorManagementMapper.class);
        BatchPreparationMapper batchPreparationMapper = mock(BatchPreparationMapper.class);
        StudentCatalogQuery expectedQuery = new StudentCatalogQuery(
                null,
                null,
                null,
                10,
                20);
        when(studentAdminMapper.countStudents(expectedQuery)).thenReturn(0L);
        when(studentAdminMapper.findStudents(expectedQuery)).thenReturn(List.of());

        AdminService service = new AdminService(
                auditService,
                adminCatalogMapper,
                studentAdminMapper,
                dashboardMapper,
                batchCatalogMapper,
                referenceDataCacheService,
                majorManagementMapper,
                batchPreparationMapper);
        Map<String, Object> result = service.students(" ", "", null, 3, 10);

        verify(studentAdminMapper).countStudents(expectedQuery);
        verify(studentAdminMapper).findStudents(expectedQuery);
        assertEquals(3, result.get("page"));
        assertEquals(10, result.get("size"));
        assertEquals(0, result.get("total"));
    }
}
