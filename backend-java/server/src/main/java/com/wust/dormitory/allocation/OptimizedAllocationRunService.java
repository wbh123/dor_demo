package com.wust.dormitory.allocation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OptimizedAllocationRunService {
    private static final String ALGORITHM_VERSION = "optimized-allocation-v1";

    private final NamedParameterJdbcTemplate jdbc;
    private final AdminAllocationService baselineAllocationService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public OptimizedAllocationRunService(
            NamedParameterJdbcTemplate jdbc,
            AdminAllocationService baselineAllocationService,
            ObjectMapper objectMapper,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.baselineAllocationService = baselineAllocationService;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> createRun(
            long batchId,
            long randomSeed,
            Duration ttl,
            CurrentUser operator) {
        if (ttl == null || ttl.compareTo(Duration.ofMinutes(5)) < 0
                || ttl.compareTo(Duration.ofHours(24)) > 0) {
            throw new BusinessException(
                    "ALLOCATION_RUN_TTL_INVALID",
                    "优化分配运行有效期必须在5分钟至24小时之间");
        }
        Map<String, Object> batch = batchMetadata(batchId);
        Map<String, Object> plan = baselineAllocationService.preview(batchId, randomSeed);
        List<Map<String, Object>> assignments =
                (List<Map<String, Object>>) plan.getOrDefault("assignments", List.of());
        List<Map<String, Object>> unassigned =
                (List<Map<String, Object>>) plan.getOrDefault("unassigned", List.of());
        if (assignments.isEmpty()) {
            throw new BusinessException(
                    "ALLOCATION_RUN_EMPTY",
                    "当前没有可以生成候选分配的学生和床位",
                    HttpStatus.CONFLICT);
        }

        List<Candidate> candidates = assignments.stream().map(this::candidate).toList();
        validateCandidateSet(candidates);
        String inputDigest = inputDigest(batchId);
        Map<String, Object> metrics = metrics(candidates, unassigned.size());
        Instant expiresAt = Instant.now().plus(ttl);

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO allocation_optimization_run
                (batch_id, algorithm_version, input_digest,
                 matching_scheme_revision_id, batch_rule_revision_id,
                 random_seed, metrics_json, run_status, version,
                 created_by, created_at, expires_at)
                VALUES
                (:batchId, :algorithmVersion, :inputDigest,
                 :matchingSchemeRevisionId, :batchRuleRevisionId,
                 :randomSeed, CAST(:metrics AS JSON), 'READY', 1,
                 :createdBy, CURRENT_TIMESTAMP(3), :expiresAt)
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("algorithmVersion", ALGORITHM_VERSION)
                .addValue("inputDigest", inputDigest)
                .addValue("matchingSchemeRevisionId", batch.get("matching_weight_scheme_id"))
                .addValue("batchRuleRevisionId", batch.get("rule_template_id"))
                .addValue("randomSeed", randomSeed)
                .addValue("metrics", json(metrics))
                .addValue("createdBy", operator.userId())
                .addValue("expiresAt", LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC)),
                keyHolder,
                new String[]{"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("优化分配运行创建成功但未返回编号");
        }
        long runId = key.longValue();
        for (Candidate candidate : candidates) {
            jdbc.update("""
                    INSERT INTO allocation_optimization_candidate
                    (run_id, student_id, bed_id, room_id, team_id,
                     score, candidate_version, explanation_json, created_at)
                    VALUES
                    (:runId, :studentId, :bedId, :roomId, :teamId,
                     :score, 1, CAST(:explanation AS JSON), CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource()
                    .addValue("runId", runId)
                    .addValue("studentId", candidate.studentId())
                    .addValue("bedId", candidate.bedId())
                    .addValue("roomId", candidate.roomId())
                    .addValue("teamId", candidate.teamId())
                    .addValue("score", candidate.score())
                    .addValue("explanation", json(Map.of(
                            "algorithmVersion", ALGORITHM_VERSION,
                            "source", "BASELINE_PREVIEW"))));
        }
        Map<String, Object> result = runView(runId);
        auditService.success(
                operator,
                "ALLOCATION_OPTIMIZATION_RUN_CREATE",
                "ALLOCATION_OPTIMIZATION_RUN",
                runId,
                "创建不可变输入摘要的优化分配候选运行",
                null,
                result);
        return result;
    }

    public Map<String, Object> run(long runId) {
        return runView(runId);
    }

    @Transactional
    public Map<String, Object> localSwap(
            long runId,
            long leftStudentId,
            long rightStudentId,
            int expectedVersion,
            String reason,
            CurrentUser operator) {
        if (leftStudentId == rightStudentId) {
            throw new BusinessException("ALLOCATION_SWAP_INVALID", "请选择两名不同学生");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("ALLOCATION_SWAP_REASON_REQUIRED", "请填写局部交换原因");
        }
        Map<String, Object> run = lockedRun(runId);
        int version = ((Number) run.get("version")).intValue();
        if (version != expectedVersion) {
            throw versionConflict();
        }
        validateRunState(runState(run), String.valueOf(run.get("input_digest")), Instant.now());

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT student_id, bed_id, room_id, team_id, score
                FROM allocation_optimization_candidate
                WHERE run_id=:runId
                  AND student_id IN (:studentIds)
                ORDER BY student_id
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("runId", runId)
                .addValue("studentIds", List.of(leftStudentId, rightStudentId)));
        if (rows.size() != 2) {
            throw new BusinessException(
                    "ALLOCATION_SWAP_CANDIDATE_NOT_FOUND",
                    "待交换学生不在当前候选方案中",
                    HttpStatus.NOT_FOUND);
        }
        Candidate left = candidateRow(rows.get(0));
        Candidate right = candidateRow(rows.get(1));
        if (left.teamId() != null || right.teamId() != null) {
            throw new BusinessException(
                    "ALLOCATION_SWAP_TEAM_MEMBER_BLOCKED",
                    "局部交换不能拆分锁定队伍，请重新生成候选方案",
                    HttpStatus.CONFLICT);
        }
        List<Candidate> swapped = swap(left, right);
        for (Candidate candidate : swapped) {
            requireHardConstraint(((Number) run.get("batch_id")).longValue(), candidate);
            jdbc.update("""
                    UPDATE allocation_optimization_candidate
                    SET bed_id=:bedId,
                        room_id=:roomId,
                        candidate_version=candidate_version+1,
                        explanation_json=CAST(:explanation AS JSON),
                        updated_at=CURRENT_TIMESTAMP(3)
                    WHERE run_id=:runId AND student_id=:studentId
                    """, new MapSqlParameterSource()
                    .addValue("runId", runId)
                    .addValue("studentId", candidate.studentId())
                    .addValue("bedId", candidate.bedId())
                    .addValue("roomId", candidate.roomId())
                    .addValue("explanation", json(Map.of(
                            "algorithmVersion", ALGORITHM_VERSION,
                            "source", "LOCAL_SWAP",
                            "reason", reason.trim()))));
        }
        jdbc.update("""
                UPDATE allocation_optimization_run
                SET version=version+1,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:runId AND version=:expectedVersion
                """, Map.of("runId", runId, "expectedVersion", expectedVersion));
        auditService.success(
                operator,
                "ALLOCATION_OPTIMIZATION_LOCAL_SWAP",
                "ALLOCATION_OPTIMIZATION_RUN",
                runId,
                reason.trim(),
                List.of(left, right),
                swapped);
        return runView(runId);
    }

    @Transactional
    public Map<String, Object> commit(long runId, CurrentUser operator) {
        Map<String, Object> run = lockedRun(runId);
        long batchId = ((Number) run.get("batch_id")).longValue();
        String currentDigest = inputDigest(batchId);
        validateRunState(runState(run), currentDigest, Instant.now());
        requireCommittableBatch(batchId);

        List<Candidate> candidates = candidateRows(runId);
        validateCandidateSet(candidates);
        int eligibleCount = scalarInt("""
                SELECT COUNT(*)
                FROM batch_student_eligibility eligibility
                LEFT JOIN bed_assignment assignment
                  ON assignment.batch_id=eligibility.batch_id
                 AND assignment.student_id=eligibility.student_id
                WHERE eligibility.batch_id=:batchId
                  AND eligibility.eligibility_status='ELIGIBLE'
                  AND assignment.id IS NULL
                """, Map.of("batchId", batchId));
        if (candidates.size() != eligibleCount) {
            throw new BusinessException(
                    "ALLOCATION_CANDIDATE_INCOMPLETE",
                    "候选方案未覆盖全部待分配学生，不能正式提交",
                    HttpStatus.CONFLICT);
        }
        Map<Long, Integer> teamSizes = lockedTeamSizes(batchId);
        requireCompleteTeams(candidates, teamSizes);
        for (Candidate candidate : candidates) {
            requireHardConstraint(batchId, candidate);
        }

        for (Candidate candidate : candidates) {
            GeneratedKeyHolder assignmentKey = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO bed_assignment
                    (batch_id, student_id, bed_id, team_id,
                     assignment_method, assignment_status,
                     assigned_by, assigned_at)
                    VALUES
                    (:batchId, :studentId, :bedId, :teamId,
                     'ADMIN_OPTIMIZED', 'ACTIVE', :operatorId,
                     CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("studentId", candidate.studentId())
                    .addValue("bedId", candidate.bedId())
                    .addValue("teamId", candidate.teamId())
                    .addValue("operatorId", operator.userId()),
                    assignmentKey,
                    new String[]{"id"});
            Number assignmentId = assignmentKey.getKey();
            if (assignmentId == null) {
                throw new IllegalStateException("正式分配成功但未返回分配编号");
            }
            jdbc.update("""
                    INSERT INTO assignment_history
                    (assignment_id, batch_id, student_id, bed_id,
                     event_type, assignment_method, operator_user_id,
                     reason, current_data, occurred_at)
                    VALUES
                    (:assignmentId, :batchId, :studentId, :bedId,
                     'CREATED', 'ADMIN_OPTIMIZED', :operatorId,
                     :reason, CAST(:currentData AS JSON), CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource()
                    .addValue("assignmentId", assignmentId.longValue())
                    .addValue("batchId", batchId)
                    .addValue("studentId", candidate.studentId())
                    .addValue("bedId", candidate.bedId())
                    .addValue("operatorId", operator.userId())
                    .addValue("reason", "提交优化分配运行 " + runId)
                    .addValue("currentData", json(candidate)));
        }
        for (Long teamId : teamSizes.keySet()) {
            jdbc.update("""
                    UPDATE selection_team
                    SET team_status='COMPLETED'
                    WHERE id=:teamId AND team_status='LOCKED'
                    """, Map.of("teamId", teamId));
        }
        jdbc.update("""
                UPDATE selection_batch
                SET batch_status='FINISHED'
                WHERE id=:batchId
                """, Map.of("batchId", batchId));
        int changed = jdbc.update("""
                UPDATE allocation_optimization_run
                SET run_status='SUBMITTED',
                    submitted_by=:operatorId,
                    submitted_at=CURRENT_TIMESTAMP(3),
                    version=version+1,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:runId
                  AND run_status='READY'
                  AND submitted_at IS NULL
                """, Map.of("runId", runId, "operatorId", operator.userId()));
        if (changed != 1) {
            throw new BusinessException(
                    "ALLOCATION_RUN_ALREADY_SUBMITTED",
                    "该优化分配运行已经提交",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> result = Map.of(
                "runId", runId,
                "batchId", batchId,
                "assignedCount", candidates.size(),
                "status", "SUBMITTED");
        auditService.success(
                operator,
                "ALLOCATION_OPTIMIZATION_COMMIT",
                "ALLOCATION_OPTIMIZATION_RUN",
                runId,
                "重新验证全部硬约束后单事务提交优化分配",
                null,
                result);
        return result;
    }

    public Map<String, Object> fairnessComparison(long runId) {
        Map<String, Object> run = runView(runId);
        long batchId = ((Number) run.get("batch_id")).longValue();
        long randomSeed = ((Number) run.get("random_seed")).longValue();
        Map<String, Object> baseline = baselineAllocationService.preview(batchId, randomSeed);
        List<Candidate> candidates = candidateRows(runId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", runId);
        result.put("optimized", metrics(candidates, 0));
        result.put("baselineSummary", baseline.get("summary"));
        result.put("algorithmVersion", ALGORITHM_VERSION);
        return result;
    }

    public String exportCsv(long runId) {
        Map<String, Object> run = runView(runId);
        StringBuilder csv = new StringBuilder("runId,batchId,studentId,bedId,roomId,teamId,score\n");
        for (Candidate candidate : candidateRows(runId)) {
            csv.append(runId).append(',')
                    .append(run.get("batch_id")).append(',')
                    .append(candidate.studentId()).append(',')
                    .append(candidate.bedId()).append(',')
                    .append(candidate.roomId()).append(',')
                    .append(candidate.teamId() == null ? "" : candidate.teamId()).append(',')
                    .append(candidate.score()).append('\n');
        }
        return csv.toString();
    }

    static void validateRunState(
            RunState state,
            String currentInputDigest,
            Instant now) {
        if (!"READY".equals(state.status()) || state.submittedAt() != null) {
            throw new BusinessException(
                    "ALLOCATION_RUN_NOT_READY",
                    "优化分配运行已经提交或当前状态不可提交",
                    HttpStatus.CONFLICT);
        }
        if (!state.expiresAt().isAfter(now)) {
            throw new BusinessException(
                    "ALLOCATION_RUN_EXPIRED",
                    "优化分配运行已经过期，请重新生成",
                    HttpStatus.CONFLICT);
        }
        if (!state.inputDigest().equals(currentInputDigest)) {
            throw new BusinessException(
                    "ALLOCATION_RUN_INPUT_CHANGED",
                    "批次、学生或床位数据已经变化，请重新生成候选方案",
                    HttpStatus.CONFLICT);
        }
    }

    static void validateCandidateSet(List<Candidate> candidates) {
        Set<Long> students = new HashSet<>();
        Set<Long> beds = new HashSet<>();
        for (Candidate candidate : candidates) {
            if (!students.add(candidate.studentId())) {
                throw new BusinessException(
                        "ALLOCATION_DUPLICATE_STUDENT",
                        "候选方案中同一学生出现多次",
                        HttpStatus.CONFLICT);
            }
            if (!beds.add(candidate.bedId())) {
                throw new BusinessException(
                        "ALLOCATION_DUPLICATE_BED",
                        "候选方案中同一床位被重复分配",
                        HttpStatus.CONFLICT);
            }
        }
    }

    static void requireCompleteTeams(
            List<Candidate> candidates,
            Map<Long, Integer> expectedTeamSizes) {
        Map<Long, List<Candidate>> grouped = new HashMap<>();
        for (Candidate candidate : candidates) {
            if (candidate.teamId() != null) {
                grouped.computeIfAbsent(candidate.teamId(), ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }
        for (Map.Entry<Long, Integer> entry : expectedTeamSizes.entrySet()) {
            List<Candidate> members = grouped.getOrDefault(entry.getKey(), List.of());
            long roomCount = members.stream().map(Candidate::roomId).distinct().count();
            if (members.size() != entry.getValue() || roomCount != 1) {
                throw new BusinessException(
                        "ALLOCATION_TEAM_PARTIAL",
                        "锁定队伍必须完整分配到同一寝室，不能部分成功",
                        HttpStatus.CONFLICT);
            }
        }
    }

    static List<Candidate> swap(Candidate left, Candidate right) {
        return List.of(
                new Candidate(
                        left.studentId(), right.bedId(), right.roomId(),
                        left.teamId(), left.score()),
                new Candidate(
                        right.studentId(), left.bedId(), left.roomId(),
                        right.teamId(), right.score()));
    }

    private Map<String, Object> runView(long runId) {
        Map<String, Object> run = one("""
                SELECT run.id, run.batch_id, run.algorithm_version,
                       run.input_digest, run.matching_scheme_revision_id,
                       run.batch_rule_revision_id, run.random_seed,
                       run.metrics_json, run.run_status, run.version,
                       run.created_at, run.expires_at, run.submitted_at,
                       creator.display_name AS created_by_name,
                       submitter.display_name AS submitted_by_name
                FROM allocation_optimization_run run
                JOIN app_user creator ON creator.id=run.created_by
                LEFT JOIN app_user submitter ON submitter.id=run.submitted_by
                WHERE run.id=:runId
                """, Map.of("runId", runId),
                "ALLOCATION_RUN_NOT_FOUND",
                "优化分配运行不存在");
        Map<String, Object> result = new LinkedHashMap<>(run);
        result.put("candidates", candidateRows(runId));
        return result;
    }

    private Map<String, Object> lockedRun(long runId) {
        return one("""
                SELECT id, batch_id, input_digest, random_seed,
                       run_status, version, expires_at, submitted_at
                FROM allocation_optimization_run
                WHERE id=:runId
                FOR UPDATE
                """, Map.of("runId", runId),
                "ALLOCATION_RUN_NOT_FOUND",
                "优化分配运行不存在");
    }

    private RunState runState(Map<String, Object> run) {
        return new RunState(
                String.valueOf(run.get("run_status")),
                timestamp(run.get("expires_at")),
                run.get("submitted_at") == null ? null : timestamp(run.get("submitted_at")),
                String.valueOf(run.get("input_digest")));
    }

    private Instant timestamp(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toInstant(ZoneOffset.UTC);
        }
        return Instant.parse(String.valueOf(value));
    }

    private Map<String, Object> batchMetadata(long batchId) {
        return one("""
                SELECT id, batch_status, matching_weight_scheme_id,
                       rule_template_id, rule_version
                FROM selection_batch
                WHERE id=:batchId
                """, Map.of("batchId", batchId),
                "BATCH_NOT_FOUND",
                "选寝批次不存在");
    }

    private String inputDigest(long batchId) {
        Map<String, Object> snapshot = one("""
                SELECT batch.id,
                       batch.batch_status,
                       batch.matching_weight_scheme_id,
                       batch.rule_template_id,
                       batch.rule_version,
                       (SELECT COUNT(*) FROM batch_student_eligibility e
                         WHERE e.batch_id=batch.id AND e.eligibility_status='ELIGIBLE') AS eligible_count,
                       (SELECT COALESCE(SUM(e.student_id),0) FROM batch_student_eligibility e
                         WHERE e.batch_id=batch.id AND e.eligibility_status='ELIGIBLE') AS eligible_sum,
                       (SELECT COUNT(*) FROM bed candidate_bed
                         JOIN room candidate_room ON candidate_room.id=candidate_bed.room_id
                         JOIN dormitory_floor candidate_floor ON candidate_floor.id=candidate_room.floor_id
                         WHERE candidate_bed.operational_status='ENABLED'
                           AND candidate_room.operational_status='ENABLED'
                           AND (EXISTS (SELECT 1 FROM batch_room_scope rs WHERE rs.batch_id=batch.id AND rs.room_id=candidate_room.id)
                             OR EXISTS (SELECT 1 FROM batch_building_scope bs WHERE bs.batch_id=batch.id AND bs.building_id=candidate_floor.building_id))) AS bed_count,
                       (SELECT COALESCE(SUM(candidate_bed.id),0) FROM bed candidate_bed
                         JOIN room candidate_room ON candidate_room.id=candidate_bed.room_id
                         JOIN dormitory_floor candidate_floor ON candidate_floor.id=candidate_room.floor_id
                         WHERE candidate_bed.operational_status='ENABLED'
                           AND candidate_room.operational_status='ENABLED'
                           AND (EXISTS (SELECT 1 FROM batch_room_scope rs WHERE rs.batch_id=batch.id AND rs.room_id=candidate_room.id)
                             OR EXISTS (SELECT 1 FROM batch_building_scope bs WHERE bs.batch_id=batch.id AND bs.building_id=candidate_floor.building_id))) AS bed_sum,
                       (SELECT COUNT(*) FROM bed_assignment assignment WHERE assignment.batch_id=batch.id) AS assignment_count,
                       (SELECT COALESCE(SUM(assignment.student_id + assignment.bed_id),0) FROM bed_assignment assignment WHERE assignment.batch_id=batch.id) AS assignment_sum
                FROM selection_batch batch
                WHERE batch.id=:batchId
                """, Map.of("batchId", batchId),
                "BATCH_NOT_FOUND",
                "选寝批次不存在");
        String canonical = snapshot.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + '=' + String.valueOf(entry.getValue()))
                .reduce((left, right) -> left + '|' + right)
                .orElse("");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void requireCommittableBatch(long batchId) {
        String status = String.valueOf(batchMetadata(batchId).get("batch_status"));
        if (!Set.of("CLOSED", "ALLOCATING").contains(status)) {
            throw new BusinessException(
                    "BATCH_NOT_CLOSED",
                    "仅已关闭或分配中的批次可以提交优化分配",
                    HttpStatus.CONFLICT);
        }
    }

    private void requireHardConstraint(long batchId, Candidate candidate) {
        int count = scalarInt("""
                SELECT COUNT(*)
                FROM batch_student_eligibility eligibility
                JOIN student student ON student.id=eligibility.student_id
                JOIN bed target_bed ON target_bed.id=:bedId
                JOIN room target_room ON target_room.id=target_bed.room_id
                JOIN dormitory_floor target_floor ON target_floor.id=target_room.floor_id
                WHERE eligibility.batch_id=:batchId
                  AND eligibility.student_id=:studentId
                  AND eligibility.eligibility_status='ELIGIBLE'
                  AND target_bed.room_id=:roomId
                  AND target_bed.operational_status='ENABLED'
                  AND target_room.operational_status='ENABLED'
                  AND target_room.gender_restriction=student.gender
                  AND NOT EXISTS (SELECT 1 FROM bed_assignment existing
                    WHERE existing.batch_id=:batchId
                      AND (existing.student_id=:studentId OR existing.bed_id=:bedId))
                  AND (EXISTS (SELECT 1 FROM batch_room_scope rs
                        WHERE rs.batch_id=:batchId AND rs.room_id=target_room.id)
                    OR EXISTS (SELECT 1 FROM batch_building_scope bs
                        WHERE bs.batch_id=:batchId AND bs.building_id=target_floor.building_id))
                  AND (NOT EXISTS (SELECT 1 FROM batch_bed_scope configured
                        WHERE configured.batch_id=:batchId)
                    OR EXISTS (SELECT 1 FROM batch_bed_scope allowed
                        WHERE allowed.batch_id=:batchId AND allowed.bed_id=target_bed.id))
                """, Map.of(
                "batchId", batchId,
                "studentId", candidate.studentId(),
                "bedId", candidate.bedId(),
                "roomId", candidate.roomId()));
        if (count != 1) {
            throw new BusinessException(
                    "ALLOCATION_HARD_CONSTRAINT_FAILED",
                    "候选方案不再满足性别、范围、床位状态或唯一性约束",
                    HttpStatus.CONFLICT);
        }
    }

    private Map<Long, Integer> lockedTeamSizes(long batchId) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        jdbc.queryForList("""
                SELECT team.id AS team_id, COUNT(*) AS member_count
                FROM selection_team team
                JOIN selection_team_member member ON member.team_id=team.id
                WHERE team.batch_id=:batchId
                  AND team.team_status='LOCKED'
                  AND member.member_status='LOCKED'
                GROUP BY team.id
                """, Map.of("batchId", batchId)).forEach(row -> result.put(
                ((Number) row.get("team_id")).longValue(),
                ((Number) row.get("member_count")).intValue()));
        return result;
    }

    private List<Candidate> candidateRows(long runId) {
        return jdbc.query("""
                SELECT student_id, bed_id, room_id, team_id, score
                FROM allocation_optimization_candidate
                WHERE run_id=:runId
                ORDER BY student_id
                """, Map.of("runId", runId),
                (rs, rowNum) -> new Candidate(
                        rs.getLong("student_id"),
                        rs.getLong("bed_id"),
                        rs.getLong("room_id"),
                        rs.getObject("team_id", Long.class),
                        rs.getDouble("score")));
    }

    private Candidate candidateRow(Map<String, Object> row) {
        return new Candidate(
                ((Number) row.get("student_id")).longValue(),
                ((Number) row.get("bed_id")).longValue(),
                ((Number) row.get("room_id")).longValue(),
                row.get("team_id") == null ? null : ((Number) row.get("team_id")).longValue(),
                ((Number) row.getOrDefault("score", 0.0d)).doubleValue());
    }

    private Candidate candidate(Map<String, Object> row) {
        return new Candidate(
                ((Number) row.get("studentId")).longValue(),
                ((Number) row.get("bedId")).longValue(),
                ((Number) row.get("roomId")).longValue(),
                row.get("teamId") == null ? null : ((Number) row.get("teamId")).longValue(),
                ((Number) row.getOrDefault("score", 0.0d)).doubleValue());
    }

    private Map<String, Object> metrics(List<Candidate> candidates, int unassignedCount) {
        double average = candidates.stream().mapToDouble(Candidate::score).average().orElse(0.0d);
        double minimum = candidates.stream().mapToDouble(Candidate::score).min().orElse(0.0d);
        double variance = candidates.stream()
                .mapToDouble(candidate -> Math.pow(candidate.score() - average, 2))
                .average()
                .orElse(0.0d);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("assignedCount", candidates.size());
        metrics.put("unassignedCount", unassignedCount);
        metrics.put("averageScore", round(average));
        metrics.put("minimumScore", round(minimum));
        metrics.put("standardDeviation", round(Math.sqrt(variance)));
        metrics.put("fairness", round(average <= 0.0d ? 0.0d : Math.max(0.0d, 100.0d - Math.sqrt(variance))));
        return metrics;
    }

    private double round(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private int scalarInt(String sql, Map<String, ?> parameters) {
        Integer value = jdbc.queryForObject(sql, parameters, Integer.class);
        return value == null ? 0 : value;
    }

    private Map<String, Object> one(
            String sql,
            Map<String, ?> parameters,
            String code,
            String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private BusinessException versionConflict() {
        return new BusinessException(
                "ALLOCATION_RUN_VERSION_CONFLICT",
                "候选方案已被其他管理员修改，请重新加载",
                HttpStatus.CONFLICT);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    "ALLOCATION_JSON_ERROR",
                    "优化分配数据无法序列化",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public record RunState(
            String status,
            Instant expiresAt,
            Instant submittedAt,
            String inputDigest) {
    }

    public record Candidate(
            long studentId,
            long bedId,
            long roomId,
            Long teamId,
            double score) {
    }
}
