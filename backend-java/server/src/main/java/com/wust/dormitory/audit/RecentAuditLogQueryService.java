package com.wust.dormitory.audit;

import com.wust.dormitory.audit.mapper.RecentAuditLogMapper;
import com.wust.dormitory.audit.model.persistence.RecentAuditLogRow;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RecentAuditLogQueryService {
    private final RecentAuditLogMapper mapper;

    public RecentAuditLogQueryService(RecentAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> list(int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        return mapper.findRecent(safeLimit).stream()
                .map(RecentAuditLogRow::asResponseMap)
                .toList();
    }
}
