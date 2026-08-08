package com.wust.dormitory.residency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.json.JdbcJsonNormalizer;
import com.wust.dormitory.residency.mapper.ResidencyMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ResidencyHistoryWriter {
    private final ResidencyMapper mapper;
    private final ObjectMapper objectMapper;

    public ResidencyHistoryWriter(ResidencyMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public void append(
            Long assignmentId,
            long studentId,
            long roomId,
            Long bedId,
            String eventType,
            Long operatorId,
            String reason,
            Object previous,
            Object current) {
        Map<String, Object> history = new LinkedHashMap<>();
        history.put("assignmentId", assignmentId);
        history.put("studentId", studentId);
        history.put("roomId", roomId);
        history.put("bedId", bedId);
        history.put("eventType", eventType);
        history.put("operatorId", operatorId);
        history.put("reason", reason);
        history.put("previous", json(previous));
        history.put("current", json(current));
        mapper.insertHistory(history);
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(JdbcJsonNormalizer.normalize(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("在住历史序列化失败", exception);
        }
    }
}
