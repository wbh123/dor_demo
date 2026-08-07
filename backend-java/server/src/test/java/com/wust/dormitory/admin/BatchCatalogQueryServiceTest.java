package com.wust.dormitory.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.mapper.AdminDashboardMapper;
import com.wust.dormitory.admin.mapper.BatchCatalogMapper;
import com.wust.dormitory.admin.mapper.StudentAdminMapper;
import com.wust.dormitory.admin.model.persistence.BatchCatalogRow;
import com.wust.dormitory.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchCatalogQueryServiceTest {
    @Test
    void batchListDelegatesToTypedMapperAndPreservesResponseValues() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        AuditService auditService = mock(AuditService.class);
        AdminCatalogMapper adminCatalogMapper = mock(AdminCatalogMapper.class);
        StudentAdminMapper studentAdminMapper = mock(StudentAdminMapper.class);
        AdminDashboardMapper adminDashboardMapper = mock(AdminDashboardMapper.class);
        BatchCatalogMapper batchCatalogMapper = mock(BatchCatalogMapper.class);
        ReferenceDataCacheService referenceDataCacheService = mock(ReferenceDataCacheService.class);
        BatchCatalogRow row = mock(BatchCatalogRow.class);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", 8L);
        response.put("batch_code", "2026-FALL");
        response.put("batch_name", "2026年秋季选寝");
        response.put("batch_status", "DRAFT");
        response.put("published_at", null);
        response.put("finished_at", null);
        response.put("eligible_count", 12L);
        response.put("assigned_count", 3L);
        response.put("bed_assigned_count", 3L);
        response.put("room_assigned_count", 2L);
        response.put("locked_room_count", 1L);
        response.put("unconfirmed_bed_resident_count", 1L);
        when(row.asResponseMap()).thenReturn(response);
        when(batchCatalogMapper.findBatches()).thenReturn(List.of(row));

        AdminService service = new AdminService(
                jdbc,
                objectMapper,
                auditService,
                adminCatalogMapper,
                studentAdminMapper,
                adminDashboardMapper,
                batchCatalogMapper,
                referenceDataCacheService);

        List<Map<String, Object>> result = service.batches();

        verify(batchCatalogMapper).findBatches();
        assertEquals(1, result.size());
        assertEquals(response, result.getFirst());
        assertEquals(null, result.getFirst().get("finished_at"));
    }
}
