package com.wust.dormitory.audit;

import com.wust.dormitory.audit.mapper.RecentAuditLogMapper;
import com.wust.dormitory.audit.model.persistence.RecentAuditLogRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecentAuditLogQueryServiceTest {
    @Test
    void clampsLimitToOneAndPreservesExistingResponseKeys() {
        RecentAuditLogMapper mapper = mock(RecentAuditLogMapper.class);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 6, 12, 30);
        when(mapper.findRecent(1)).thenReturn(List.of(new RecentAuditLogRow(
                10L,
                "req-10",
                7L,
                "ADMIN",
                "ROOM_UPDATE",
                "ROOM",
                "88",
                "SUCCESS",
                "容量调整",
                occurredAt)));

        RecentAuditLogQueryService service = new RecentAuditLogQueryService(mapper);
        List<Map<String, Object>> result = service.list(0);

        verify(mapper).findRecent(1);
        assertEquals(1, result.size());
        Map<String, Object> item = result.getFirst();
        assertEquals(10L, item.get("id"));
        assertEquals("req-10", item.get("request_id"));
        assertEquals(7L, item.get("operator_user_id"));
        assertEquals("ROOM_UPDATE", item.get("action_type"));
        assertEquals("88", item.get("resource_id"));
        assertEquals("容量调整", item.get("reason"));
        assertEquals(occurredAt, item.get("occurred_at"));
    }

    @Test
    void clampsLargeLimitToFiveHundred() {
        RecentAuditLogMapper mapper = mock(RecentAuditLogMapper.class);
        when(mapper.findRecent(500)).thenReturn(List.of());

        RecentAuditLogQueryService service = new RecentAuditLogQueryService(mapper);
        service.list(999);

        verify(mapper).findRecent(500);
    }
}
