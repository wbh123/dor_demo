package com.wust.dormitory.allocation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OptimizedAllocationRunService {
    private static final String ALGORITHM_VERSION = "optimized-allocation-v1";
    private static final int CANDIDATE_INSERT_BATCH_SIZE = 200;

    private final OptimizedAllocationMapper mapper;
    private final AllocationSnapshotReader snapshotReader;
    private final AllocationInputDigestService digestService;
    private final OptimizedAllocationConstraintValidator constraintValidator;
    private final AssignmentWriteService assignmentWriteService;
    private final OptimizedAllocationRunQueryService queryService;
    private final BaselineAllocationPlanner baselinePlanner = new BaselineAllocationPlanner();
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public OptimizedAllocationRunService(
            OptimizedAllocationMapper mapper,
            AllocationSnapshotReader snapshotReader,
            AllocationInputDigestService digestService,
            OptimizedAllocationConstraintValidator constraintValidator,
            AssignmentWriteService assignmentWriteService,
            OptimizedAllocationRunQueryService queryService,
            ObjectMapper objectMapper,
            AuditService auditService) {
        this.mapper = mapper;
        this.snapshotReader = snapshotReader;
        this.digestService = digestService;
        this.constraintValidator = constraintValidator;
        this.assignmentWriteService = assignmentWriteService;
        this.queryService = queryService;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> createRun(long batchId, long randomSeed, Duration ttl, CurrentUser operator) {
        requireValidTtl(ttl);
        Map<String, Object> batch = requireBatchMetadata(batchId, false);
        AllocationModels.InputSnapshot snapshot = snapshotReader.read(batchId);
        AllocationModels.Plan plan = baselinePlanner.plan(snapshot, randomSeed);
        List<Candidate> candidates = plan.assignments().stream().map(this::candidate).toList();
        if (candidates.isEmpty()) {
            throw new BusinessException("ALLOCATION_RUN_EMPTY", "当前没有可以生成候选分配的学生和床位", HttpStatus.CONFLICT);
        }
        validateCandidateSet(candidates);
        Map<String, Object> run = new HashMap<>();
        run.put("batchId", batchId);
        run.put("algorithmVersion", ALGORITHM_VERSION);
        run.put("inputDigest", digestService.digest(snapshot, batch));
        run.put("matchingSchemeRevisionId", batch.get("matching_weight_scheme_id"));
        run.put("batchRuleRevisionId", batch.get("rule_template_id"));
        run.put("randomSeed", randomSeed);
        run.put("metrics", json(OptimizedAllocationMetrics.calculate(candidates, plan.unassigned().size())));
        run.put("createdBy", operator.userId());
        run.put("expiresAt", LocalDateTime.ofInstant(Instant.now().plus(ttl), ZoneOffset.UTC));
        mapper.insertRun(run);
        long runId = generatedId(run);
        insertCandidates(runId, candidates);
        Map<String, Object> result = queryService.runView(runId);
        auditService.success(operator, "ALLOCATION_OPTIMIZATION_RUN_CREATE", "ALLOCATION_OPTIMIZATION_RUN",
                runId, "创建不可变输入摘要的优化分配候选运行", null, result);
        return result;
    }

    public Map<String, Object> run(long runId) {
        return queryService.runView(runId);
    }

    @Transactional
    public Map<String, Object> localSwap(
            long runId, long leftStudentId, long rightStudentId,
            int expectedVersion, String reason, CurrentUser operator) {
        if (leftStudentId == rightStudentId) throw new BusinessException("ALLOCATION_SWAP_INVALID", "请选择两名不同学生");
        if (reason == null || reason.isBlank()) throw new BusinessException("ALLOCATION_SWAP_REASON_REQUIRED", "请填写局部交换原因");
        Map<String, Object> run = lockRun(runId);
        if (((Number) run.get("version")).intValue() != expectedVersion) throw versionConflict();
        long batchId = number(run.get("batch_id"));
        AllocationModels.InputSnapshot snapshot = snapshotReader.read(batchId);
        validateRunState(runState(run), digestService.digest(snapshot, requireBatchMetadata(batchId, false)), Instant.now());
        List<Map<String, Object>> rows = mapper.lockCandidates(runId, List.of(leftStudentId, rightStudentId));
        if (rows.size() != 2) {
            throw new BusinessException("ALLOCATION_SWAP_CANDIDATE_NOT_FOUND", "待交换学生不在当前候选方案中", HttpStatus.NOT_FOUND);
        }
        Candidate left = queryService.candidate(rows.get(0));
        Candidate right = queryService.candidate(rows.get(1));
        if (left.teamId() != null || right.teamId() != null) {
            throw new BusinessException("ALLOCATION_SWAP_TEAM_MEMBER_BLOCKED", "局部交换不能拆分锁定队伍，请重新生成候选方案", HttpStatus.CONFLICT);
        }
        List<Candidate> swapped = swap(left, right);
        constraintValidator.requireHardConstraints(snapshot, swapped);
        for (Candidate candidate : swapped) updateCandidate(runId, candidate, reason.trim());
        if (mapper.bumpRunVersion(runId, expectedVersion) != 1) throw versionConflict();
        auditService.success(operator, "ALLOCATION_OPTIMIZATION_LOCAL_SWAP", "ALLOCATION_OPTIMIZATION_RUN",
                runId, reason.trim(), List.of(left, right), swapped);
        return queryService.runView(runId);
    }

    @Transactional
    public Map<String, Object> commit(long runId, CurrentUser operator) {
        Map<String, Object> run = lockRun(runId);
        long batchId = number(run.get("batch_id"));
        Map<String, Object> batch = requireBatchMetadata(batchId, true);
        requireCommittableStatus(String.valueOf(batch.get("batch_status")));
        AllocationModels.InputSnapshot snapshot = snapshotReader.read(batchId);
        validateRunState(runState(run), digestService.digest(snapshot, batch), Instant.now());
        List<Candidate> candidates = queryService.candidates(runId);
        validateCandidateSet(candidates);
        if (candidates.size() != snapshot.students().size()) {
            throw new BusinessException("ALLOCATION_CANDIDATE_INCOMPLETE", "候选方案未覆盖全部待分配学生，不能正式提交", HttpStatus.CONFLICT);
        }
        requireCompleteTeams(candidates, constraintValidator.expectedLockedTeamSizes(snapshot));
        constraintValidator.requireHardConstraints(snapshot, candidates);
        List<AssignmentWriteService.WriteItem> writes = candidates.stream()
                .map(candidate -> new AssignmentWriteService.WriteItem(
                        candidate.studentId(), candidate.bedId(), candidate.teamId(), candidate))
                .toList();
        assignmentWriteService.write(batchId, writes, "ADMIN_OPTIMIZED", null,
                operator.userId(), "提交优化分配运行 " + runId);
        if (mapper.markSubmitted(runId, operator.userId()) != 1) {
            throw new BusinessException("ALLOCATION_RUN_ALREADY_SUBMITTED", "该优化分配运行已经提交", HttpStatus.CONFLICT);
        }
        Map<String, Object> result = Map.of(
                "runId", runId, "batchId", batchId,
                "assignedCount", candidates.size(), "status", "SUBMITTED");
        auditService.success(operator, "ALLOCATION_OPTIMIZATION_COMMIT", "ALLOCATION_OPTIMIZATION_RUN",
                runId, "重新验证全部硬约束后单事务提交优化分配", null, result);
        return result;
    }

    public Map<String, Object> fairnessComparison(long runId) {
        Map<String, Object> run = queryService.runView(runId);
        long batchId = number(run.get("batch_id"));
        AllocationModels.Plan baseline = baselinePlanner.plan(
                snapshotReader.read(batchId), number(run.get("random_seed")));
        return Map.of(
                "runId", runId,
                "optimized", OptimizedAllocationMetrics.calculate(queryService.candidates(runId), 0),
                "baselineSummary", baseline.summary(),
                "algorithmVersion", ALGORITHM_VERSION);
    }

    public String exportCsv(long runId) {
        return queryService.exportCsv(runId);
    }

    static void validateRunState(RunState state, String currentInputDigest, Instant now) {
        OptimizedAllocationRules.validateRunState(state, currentInputDigest, now);
    }

    static void validateCandidateSet(List<Candidate> candidates) {
        OptimizedAllocationRules.validateCandidateSet(candidates);
    }

    static void requireCompleteTeams(List<Candidate> candidates, Map<Long, Integer> expectedTeamSizes) {
        OptimizedAllocationRules.requireCompleteTeams(candidates, expectedTeamSizes);
    }

    static List<Candidate> swap(Candidate left, Candidate right) {
        return OptimizedAllocationRules.swap(left, right);
    }

    private void insertCandidates(long runId, List<Candidate> candidates) {
        for (int start = 0; start < candidates.size(); start += CANDIDATE_INSERT_BATCH_SIZE) {
            int end = Math.min(start + CANDIDATE_INSERT_BATCH_SIZE, candidates.size());
            mapper.insertCandidates(runId, candidates.subList(start, end).stream().map(this::candidateInsertRow).toList());
        }
    }

    private Map<String, Object> candidateInsertRow(Candidate candidate) {
        Map<String, Object> row = new HashMap<>();
        row.put("studentId", candidate.studentId());
        row.put("bedId", candidate.bedId());
        row.put("roomId", candidate.roomId());
        row.put("teamId", candidate.teamId());
        row.put("score", candidate.score());
        row.put("explanation", json(Map.of("algorithmVersion", ALGORITHM_VERSION, "source", "BASELINE_PREVIEW")));
        return row;
    }

    private void updateCandidate(long runId, Candidate candidate, String reason) {
        Map<String, Object> values = new HashMap<>();
        values.put("runId", runId);
        values.put("studentId", candidate.studentId());
        values.put("bedId", candidate.bedId());
        values.put("roomId", candidate.roomId());
        values.put("explanation", json(Map.of("algorithmVersion", ALGORITHM_VERSION, "source", "LOCAL_SWAP", "reason", reason)));
        mapper.updateCandidate(values);
    }

    private Map<String, Object> lockRun(long runId) {
        Map<String, Object> run = mapper.lockRun(runId);
        if (run == null) throw new BusinessException("ALLOCATION_RUN_NOT_FOUND", "优化分配运行不存在", HttpStatus.NOT_FOUND);
        return run;
    }

    private Map<String, Object> requireBatchMetadata(long batchId, boolean lock) {
        Map<String, Object> batch = lock ? mapper.lockBatchMetadata(batchId) : mapper.findBatchMetadata(batchId);
        if (batch == null) throw new BusinessException("BATCH_NOT_FOUND", "选寝批次不存在", HttpStatus.NOT_FOUND);
        return batch;
    }

    private RunState runState(Map<String, Object> run) {
        return new RunState(String.valueOf(run.get("run_status")), timestamp(run.get("expires_at")),
                run.get("submitted_at") == null ? null : timestamp(run.get("submitted_at")),
                String.valueOf(run.get("input_digest")));
    }

    private Instant timestamp(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        return Instant.parse(String.valueOf(value));
    }

    private void requireValidTtl(Duration ttl) {
        if (ttl == null || ttl.compareTo(Duration.ofMinutes(5)) < 0 || ttl.compareTo(Duration.ofHours(24)) > 0) {
            throw new BusinessException("ALLOCATION_RUN_TTL_INVALID", "优化分配运行有效期必须在5分钟至24小时之间");
        }
    }

    private void requireCommittableStatus(String status) {
        if (!Set.of("CLOSED", "ALLOCATING").contains(status)) {
            throw new BusinessException("BATCH_NOT_CLOSED", "仅已关闭或分配中的批次可以提交优化分配", HttpStatus.CONFLICT);
        }
    }

    private Candidate candidate(AllocationModels.AssignmentItem item) {
        return new Candidate(item.studentId(), item.bedId(), item.roomId(), item.teamId(), item.score());
    }

    private long generatedId(Map<String, Object> values) {
        Object key = values.get("id");
        if (key instanceof Number number) return number.longValue();
        throw new IllegalStateException("优化分配运行创建成功但未返回编号");
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("ALLOCATION_JSON_ERROR", "优化分配数据无法序列化", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private BusinessException versionConflict() {
        return new BusinessException("ALLOCATION_RUN_VERSION_CONFLICT", "候选方案已被其他管理员修改，请重新加载", HttpStatus.CONFLICT);
    }

    public record RunState(String status, Instant expiresAt, Instant submittedAt, String inputDigest) {
    }

    public record Candidate(long studentId, long bedId, long roomId, Long teamId, double score) {
    }
}
