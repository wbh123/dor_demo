package com.wust.dormitory.notification;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class NotificationOptionService {
    private final NotificationOptionMapper mapper;

    public NotificationOptionService(NotificationOptionMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> students(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim();
        if (normalized.length() < 2) return List.of();
        return mapper.searchStudents(normalized);
    }

    public List<Map<String, Object>> batches() {
        return mapper.listBatches();
    }

    public List<Map<String, Object>> majors() {
        return mapper.listMajors();
    }

    public List<Map<String, Object>> buildings() {
        return mapper.listBuildings();
    }
}
