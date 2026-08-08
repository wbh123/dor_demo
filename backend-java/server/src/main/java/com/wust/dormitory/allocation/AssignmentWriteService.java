package com.wust.dormitory.allocation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AssignmentWriteService {
    private final AllocationCommitMapper mapper;
    private final ObjectMapper objectMapper;

    public AssignmentWriteService(AllocationCommitMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public int write(
            long batchId,
            List<WriteItem> items,
            String assignmentMethod,
            Long allocationRunId,
            long operatorId,
            String reason) {
        Set<Long> completedTeams = new LinkedHashSet<>();
        for (WriteItem item : items) {
            Map<String, Object> assignment = new HashMap<>();
            assignment.put("batchId", batchId);
            assignment.put("studentId", item.studentId());
            assignment.put("bedId", item.bedId());
            assignment.put("teamId", item.teamId());
            assignment.put("assignmentMethod", assignmentMethod);
            assignment.put("allocationRunId", allocationRunId);
            assignment.put("operatorId", operatorId);
            mapper.insertAssignment(assignment);
            long assignmentId = generatedId(assignment);

            Map<String, Object> history = new HashMap<>();
            history.put("assignmentId", assignmentId);
            history.put("batchId", batchId);
            history.put("studentId", item.studentId());
            history.put("bedId", item.bedId());
            history.put("assignmentMethod", assignmentMethod);
            history.put("operatorId", operatorId);
            history.put("reason", reason);
            history.put("currentData", json(item.currentData()));
            mapper.insertAssignmentHistory(history);
            if (item.teamId() != null) completedTeams.add(item.teamId());
        }
        if (!completedTeams.isEmpty()) mapper.completeTeams(List.copyOf(completedTeams));
        mapper.finishBatch(batchId);
        return items.size();
    }

    private long generatedId(Map<String, Object> values) {
        Object value = values.get("id");
        if (value instanceof Number number) return number.longValue();
        throw new IllegalStateException("床位分配成功但未返回编号");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("JSON_ERROR", "分配历史序列化失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public record WriteItem(long studentId, long bedId, Long teamId, Object currentData) {
    }
}
