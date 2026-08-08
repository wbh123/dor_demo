package com.wust.dormitory.allocation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AllocationCommitService {
    private final AllocationCommitMapper mapper;
    private final AllocationSnapshotReader snapshotReader;
    private final AssignmentWriteService assignmentWriteService;
    private final BaselineAllocationPlanner planner = new BaselineAllocationPlanner();
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public AllocationCommitService(
            AllocationCommitMapper mapper,
            AllocationSnapshotReader snapshotReader,
            AssignmentWriteService assignmentWriteService,
            ObjectMapper objectMapper,
            AuditService auditService) {
        this.mapper = mapper;
        this.snapshotReader = snapshotReader;
        this.assignmentWriteService = assignmentWriteService;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> commit(
            long batchId,
            long randomSeed,
            String idempotencyKey,
            CurrentUser operator) {
        Map<String, Object> batch = mapper.lockBatch(batchId);
        if (batch == null) {
            throw new BusinessException("BATCH_NOT_FOUND", "选寝批次不存在", HttpStatus.NOT_FOUND);
        }
        String status = String.valueOf(batch.get("batch_status"));
        if (!Set.of("CLOSED", "ALLOCATING").contains(status)) {
            throw new BusinessException("BATCH_NOT_CLOSED", "仅已关闭或分配中的批次可以执行统一分配");
        }
        Map<String, Object> existing = mapper.findExistingRun(batchId, idempotencyKey);
        if (existing != null) {
            long runId = ((Number) existing.get("id")).longValue();
            Map<String, Object> reused = new LinkedHashMap<>();
            reused.put("allocationRunId", runId);
            reused.put("executionCode", existing.get("execution_code"));
            reused.put("reused", true);
            reused.put("summary", existing.get("summary_json"));
            reused.put("unassigned", mapper.findUnassignedFailures(runId));
            return reused;
        }

        AllocationModels.Plan plan = planner.plan(snapshotReader.read(batchId), randomSeed);
        String executionCode = "ALLOC-" + batchId + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Map<String, Object> run = new HashMap<>();
        run.put("batchId", batchId);
        run.put("executionCode", executionCode);
        run.put("idempotencyKey", idempotencyKey);
        run.put("runStatus", plan.unassigned().isEmpty() ? "SUCCEEDED" : "PARTIAL_SUCCESS");
        run.put("algorithmVersion", BaselineAllocationPlanner.ALGORITHM_VERSION);
        run.put("randomSeed", randomSeed);
        run.put("studentSnapshot", json(plan.studentSnapshot()));
        run.put("bedSnapshot", json(plan.bedSnapshot()));
        run.put("summary", json(plan.summary()));
        run.put("operatorId", operator.userId());
        mapper.insertRun(run);
        long runId = generatedId(run, "统一分配运行");

        List<AssignmentWriteService.WriteItem> writes = plan.assignments().stream()
                .map(item -> new AssignmentWriteService.WriteItem(
                        item.studentId(), item.bedId(), item.teamId(), item.toMap()))
                .toList();
        assignmentWriteService.write(
                batchId,
                writes,
                "ADMIN_RANDOM",
                runId,
                operator.userId(),
                "统一分配运行 " + runId);
        insertAssignedResults(runId, plan.assignments());
        insertUnassigned(runId, plan.unassigned());

        auditService.success(
                operator,
                "ALLOCATION_COMMIT",
                "ALLOCATION_RUN",
                runId,
                "锁定队伍优先，其余全部学生按个人参与统一分配",
                null,
                plan.toMap());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allocationRunId", runId);
        result.put("executionCode", executionCode);
        result.put("reused", false);
        result.put("summary", plan.summary());
        result.put("unassigned", plan.unassigned().stream().map(AllocationModels.UnassignedItem::toMap).toList());
        return result;
    }

    private void insertAssignedResults(long runId, List<AllocationModels.AssignmentItem> assignments) {
        for (AllocationModels.AssignmentItem item : assignments) {
            mapper.insertAssignedResult(Map.of(
                    "runId", runId,
                    "studentId", item.studentId(),
                    "bedId", item.bedId(),
                    "score", item.score(),
                    "explanation", json(Map.of(
                            "algorithm", BaselineAllocationPlanner.ALGORITHM_VERSION,
                            "teamPreserved", item.teamId() != null,
                            "genderMatched", true))));
        }
    }

    private void insertUnassigned(long runId, List<AllocationModels.UnassignedItem> unassigned) {
        if (unassigned.isEmpty()) return;
        List<Map<String, Object>> rows = new ArrayList<>(unassigned.size());
        for (AllocationModels.UnassignedItem item : unassigned) {
            rows.add(Map.of(
                    "studentId", item.studentId(),
                    "failureCode", item.failureCode(),
                    "explanation", json(Map.of(
                            "studentName", item.studentName(),
                            "studentNumber", item.studentNumber(),
                            "failureReason", item.failureReason()))));
        }
        mapper.insertUnassignedResults(runId, rows);
    }

    private long generatedId(Map<String, Object> values, String entity) {
        Object value = values.get("id");
        if (value instanceof Number number) return number.longValue();
        throw new IllegalStateException(entity + "创建成功但未返回编号");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("JSON_ERROR", "分配快照序列化失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
