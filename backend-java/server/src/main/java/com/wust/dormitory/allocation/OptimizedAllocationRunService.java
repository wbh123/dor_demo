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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private final BaselineAllocationPlanner baselinePlanner = new BaselineAllocationPlanner();
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public OptimizedAllocationRunService(
            OptimizedAllocationMapper mapper,
            AllocationSnapshotReader snapshotReader,
            AllocationInputDigestService digestService,
            OptimizedAllocationConstraintValidator constraintValidator,
            AssignmentWriteService assignmentWriteService,
            ObjectMapper objectMapper,
            AuditService auditService) {
        this.mapper = mapper;
        this.snapshotReader = snapshotReader;
        this.digestService = digestService;
        this.constraintValidator = constraintValidator;
        this.assignmentWriteService = assignmentWriteService;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> createRun(
            long batchId, long randomSeed, Duration ttl, CurrentUser operator) {
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
        run.put("metrics", json(metrics(candidates, plan.unassigned().size())));
        run.put("createdBy", operator.userId());
        run.put("expiresAt", LocalDateTime.ofInstant(Instant.now().plus(ttl), ZoneOffset.UTC));
        mapper.insertRun(run);
        long runId = generatedId(run);
        insertCandidates(runId, candidates);

        Map<String, Object> result = runView(runId);
        auditService.success(operator, "ALLOCATION_OPTIMIZATION_RUN_CREATE", "ALLOCATION_OPTIMIZATION_RUN",
                runId, "创建不可变输入摘要的优化分配候选运行", null, result);
        return result;
    }

    public Map<String, Object> run(long runId) {
        return runView(runId);
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
        Map<String, Object> batch = requireBatchMetadata(batchId, false);
        validateRunState(runState(run), digestService.digest(snapshot, batch), Instant.now());

        List<Map<String, Object>> rows = mapper.lockCandidates(runId, List.of(leftStudentId, rightStudentId));
        if (rows.size() != 2) {
            throw new BusinessException("ALLOCATION_SWAP_CANDIDATE_NOT_FOUND", "待交换学生不在当前候选方案中", HttpStatus.NOT_FOUND);
        }
        Candidate left = candidateRow(rows.get(0));
        Candidate right = candidateRow(rows.get(1));
        if (left.teamId() != null || right.teamId() != null) {
            throw new BusinessException("ALLOCATION_SWAP_TEAM_MEMBER_BLOCKED", "局部交换不能拆分锁定队伍，请重新生成候选方案", HttpStatus.CONFLICT);
        }
        List<Candidate> swapped = swap(left, right);
        constraintValidator.requireHardConstraints(snapshot, swapped);
        for (Candidate candidate : swapped) {
            Map<String, Object> values = new HashMap<>();
            values.put("runId", runId);
            values.put("studentId", candidate.studentId());
            values.put("bedId", candidate.bedId());
            values.put("roomId", candidate.roomId());
            values.put("explanation", json(Map.of(
                    "algorithmVersion", ALGORITHM_VERSION,
                    "source", "LOCAL_SWAP",
                    "reason", reason.trim())));
            mapper.updateCandidate(values);
        }
        if (mapper.bumpRunVersion(runId, expectedVersion) != 1) throw versionConflict();
        auditService.success(operator, "ALLOCATION_OPTIMIZATION_LOCAL_SWAP", "ALLOCATION_OPTIMIZATION_RUN",
                runId, reason.trim(), List.of(left, right), swapped);
        return runView(runId);
    }

    @Transactional
    public Map<String, Object> commit(long runId, CurrentUser operator) {
        Map<String, Object> run = lockRun(runId);
        long batchId = number(run.get("batch_id"));
        Map<String, Object> batch = requireBatchMetadata(batchId, true);
        requireCommittableStatus(String.valueOf(batch.get("batch_status")));
        AllocationModels.InputSnapshot snapshot = snapshotReader.read(batchId);
        validateRunState(runState(run), digestService.digest(snapshot, batch), Instant.now());

        List<Candidate> candidates = candidateRows(runId);
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
                "runId", runId,
                "batchId", batchId,
                "assignedCount", candidates.size(),
                "status", "SUBMITTED");
        auditService.success(operator, "ALLOCATION_OPTIMIZATION_COMMIT", "ALLOCATION_OPTIMIZATION_RUN",
                runId, "重新验证全部硬约束后单事务提交优化分配", null, result);
        return result;
    }

    public Map<String, Object> fairnessComparison(long runId) {
        Map<String, Object> run = runView(runId);
        long batchId = number(run.get("batch_id"));
        long randomSeed = number(run.get("random_seed"));
        AllocationModels.Plan baseline = baselinePlanner.plan(snapshotReader.read(batchId), randomSeed);
        List<Candidate> candidates = candidateRows(runId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("optimized", metrics(candidates, 0));
        result.put("baselineSummary", baseline.summary());
        result.put("algorithmVersion", ALGORITHM_VERSION);
        return result;
    }

    public String exportCsv(long runId) {
        Map<String, Object> run = runView(runId);
        StringBuilder csv = new StringBuilder("runId,batchId,studentId,bedId,roomId,teamId,score\n");
        for (Candidate candidate : candidateRows(runId)) {
            csv.append(runId).append(',').append(run.get("batch_id")).append(',')
                    .append(candidate.studentId()).append(',').append(candidate.bedId()).append(',')
                    .append(candidate.roomId()).append(',')
                    .append(candidate.teamId() == null ? "" : candidate.teamId()).append(',')
                    .append(candidate.score()).append('\n');
        }
        return csv.toString();
    }

    static void validateRunState(RunState state, String currentInputDigest, Instant now) {
        if (!"READY".equals(state.status()) || state.submittedAt() != null) {
            throw new BusinessException("ALLOCATION_RUN_NOT_READY", "优化分配运行已经提交或当前状态不可提交", HttpStatus.CONFLICT);
        }
        if (!state.expiresAt().isAfter(now)) {
            throw new BusinessException("ALLOCATION_RUN_EXPIRED", "优化分配运行已经过期，请重新生成", HttpStatus.CONFLICT);
        }
        if (!state.inputDigest().equals(currentInputDigest)) {
            throw new BusinessException("ALLOCATION_RUN_INPUT_CHANGED", "批次、学生或床位数据已经变化，请重新生成候选方案", HttpStatus.CONFLICT);
        }
    }

    static void validateCandidateSet(List<Candidate> candidates) {
        Set<Long> students = new HashSet<>();
        Set<Long> beds = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (!students.add(candidate.studentId())) {
                throw new BusinessException("ALLOCATION_DUPLICATE_STUDENT", "候选方案中同一学生出现多次", HttpStatus.CONFLICT);
            }
            if (!beds.add(candidate.bedId())) {
                throw new BusinessException("ALLOCATION_DUPLICATE_BED", "候选方案中同一床位被重复分配", HttpStatus.CONFLICT);
            }
        }
    }

    static void requireCompleteTeams(List<Candidate> candidates, Map<Long, Integer> expectedTeamSizes) {
        Map<Long, List<Candidate>> grouped = new HashMap<>();
        candidates.stream().filter(candidate -> candidate.teamId() != null)
                .forEach(candidate -> grouped.computeIfAbsent(candidate.teamId(), ignored -> new ArrayList<>()).add(candidate));
        for (Map.Entry<Long, Integer> entry : expectedTeamSizes.entrySet()) {
            List<Candidate> members = grouped.getOrDefault(entry.getKey(), List.of());
            long roomCount = members.stream().map(Candidate::roomId).distinct().count();
            if (members.size() != entry.getValue() || roomCount != 1) {
                throw new BusinessException("ALLOCATION_TEAM_PARTIAL", "锁定队伍必须完整分配到同一寝室，不能部分成功", HttpStatus.CONFLICT);
            }
        }
    }

    static List<Candidate> swap(Candidate left, Candidate right) {
        return List.of(
                new Candidate(left.studentId(), right.bedId(), right.roomId(), left.teamId(), left.score()),
                new Candidate(right.studentId(), left.bedId(), left.roomId(), right.teamId(), right.score()));
    }

    private Map<String, Object> runView(long runId) {
        Map<String, Object> run = mapper.findRunView(runId);
        if (run == null) throw notFound();
        Map<String, Object> result = new LinkedHashMap<>(run);
        result.put("candidates", candidateRows(runId));
        return result;
    }

    private Map<String, Object> lockRun(long runId) {
        Map<String, Object> run = mapper.lockRun(runId);
        if (run == null) throw notFound();
        return run;
    }

    private Map<String, Object> requireBatchMetadata(long batchId, boolean lock) {
        Map<String, Object> batch = lock ? mapper.lockBatchMetadata(batchId) : mapper.findBatchMetadata(batchId);
        if (batch == null) throw new BusinessException("BATCH_NOT_FOUND", "选寝批次不存在", HttpStatus.NOT_FOUND);
        return batch;
    }

    private List<Candidate> candidateRows(long runId) {
        return mapper.findCandidates(runId).stream().map(this::candidateRow).toList();
    }

    private Candidate candidateRow(Map<String, Object> row) {
        return new Candidate(number(row.get("student_id")), number(row.get("bed_id")), number(row.get("room_id")),
                row.get("team_id") == null ? null : number(row.get("team_id")),
                ((Number) row.getOrDefault("score", 0.0d)).doubleValue());
    }

    private Candidate candidate(AllocationModels.AssignmentItem item) {
        return new Candidate(item.studentId(), item.bedId(), item.roomId(), item.teamId(), item.score());
    }

    private void insertCandidates(long runId, List<Candidate> candidates) {
        for (int start = 0; start < candidates.size(); start += CANDIDATE_INSERT_BATCH_SIZE) {
            int end = Math.min(start + CANDIDATE_INSERT_BATCH_SIZE, candidates.size());
            List<Map<String, Object>> rows = candidates.subList(start, end).stream().map(candidate -> {
                Map<String, Object> row = new HashMap<>();
                row.put("studentId", candidate.studentId());
                row.put("bedId", candidate.bedId());
                row.put("roomId", candidate.roomId());
                row.put("teamId", candidate.teamId());
                row.put("score", candidate.score());
                row.put("explanation", json(Map.of("algorithmVersion", ALGORITHM_VERSION, "source", "BASELINE_PREVIEW")));
                return row;
            }).toList();
            mapper.insertCandidates(runId, rows);
        }
    }

    private Map<String, Object> metrics(List<Candidate> candidates, int unassignedCount) {
        double average = candidates.stream().mapToDouble(Candidate::score).average().orElse(0.0d);
        double minimum = candidates.stream().mapToDouble(Candidate::score).min().orElse(0.0d);
        double variance = candidates.stream().mapToDouble(candidate -> Math.pow(candidate.score() - average, 2)).average().orElse(0.0d);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("assignedCount", candidates.size());
        metrics.put("unassignedCount", unassignedCount);
        metrics.put("averageScore", round(average));
        metrics.put("minimumScore", round(minimum));
        metrics.put("standardDeviation", round(Math.sqrt(variance)));
        metrics.put("fairness", round(average <= 0.0d ? 0.0d : Math.max(0.0d, 100.0d - Math.sqrt(variance))));
        return metrics;
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

    private long generatedId(Map<String, Object> values) {
        Object key = values.get("id");
        if (key instanceof Number number) return number.longValue();
        throw new IllegalStateException("优化分配运行创建成功但未返回编号");
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("ALLOCATION_JSON_ERROR", "优化分配数据无法序列化", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private BusinessException notFound() {
        return new BusinessException("ALLOCATION_RUN_NOT_FOUND", "优化分配运行不存在", HttpStatus.NOT_FOUND);
    }

    private BusinessException versionConflict() {
        return new BusinessException("ALLOCATION_RUN_VERSION_CONFLICT", "候选方案已被其他管理员修改，请重新加载", HttpStatus.CONFLICT);
    }

    public record RunState(String status, Instant expiresAt, Instant submittedAt, String inputDigest) {
    }

    public record Candidate(long studentId, long bedId, long roomId, Long teamId, double score) {
    }
}
