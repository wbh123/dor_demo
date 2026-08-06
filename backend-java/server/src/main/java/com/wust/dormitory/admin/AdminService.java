package com.wust.dormitory.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.mapper.AdminDashboardMapper;
import com.wust.dormitory.admin.mapper.StudentAdminMapper;
import com.wust.dormitory.admin.model.persistence.AdminDashboardStatsRow;
import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;
import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;
import com.wust.dormitory.admin.model.persistence.StudentCatalogRow;
import com.wust.dormitory.admin.model.query.StudentCatalogQuery;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminService {
    private static final Map<String, Set<String>> BATCH_TRANSITIONS = Map.of(
            "DRAFT", Set.of("PUBLISHED", "CANCELLED"),
            "PUBLISHED", Set.of("OPEN", "CANCELLED"),
            "OPEN", Set.of("PAUSED", "CLOSED"),
            "PAUSED", Set.of("OPEN", "CLOSED"),
            "CLOSED", Set.of("ALLOCATING", "FINISHED"),
            "ALLOCATING", Set.of("FINISHED", "CLOSED")
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final AdminCatalogMapper adminCatalogMapper;
    private final StudentAdminMapper studentAdminMapper;
    private final AdminDashboardMapper adminDashboardMapper;

    public AdminService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AuditService auditService,
            AdminCatalogMapper adminCatalogMapper,
            StudentAdminMapper studentAdminMapper,
            AdminDashboardMapper adminDashboardMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.adminCatalogMapper = adminCatalogMapper;
        this.studentAdminMapper = studentAdminMapper;
        this.adminDashboardMapper = adminDashboardMapper;
    }

    public Map<String, Object> dashboard() {
        AdminDashboardStatsRow stats = adminDashboardMapper.findStats();
        return stats.asResponseMap();
    }

    public List<Map<String, Object>> majors(Boolean enabled) {
        return adminCatalogMapper.findMajors(enabled).stream()
                .map(MajorCatalogRow::asResponseMap)
                .toList();
    }

    @Transactional
    public long saveMajor(Long id, MajorCommand command, CurrentUser operator) {
        if (id == null) {
            GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO major (major_code, major_name, enabled)
                    VALUES (:code, :name, :enabled)
                    """, new MapSqlParameterSource()
                    .addValue("code", command.majorCode())
                    .addValue("name", command.majorName())
                    .addValue("enabled", command.enabled() ? 1 : 0), keyHolder, new String[]{"id"});
            long newId = keyHolder.getKey().longValue();
            auditService.success(operator, "MAJOR_CREATE", "MAJOR", newId, null, null, command);
            return newId;
        }
        Map<String, Object> before = one("SELECT * FROM major WHERE id=:id", Map.of("id", id), "MAJOR_NOT_FOUND", "专业不存在");
        jdbc.update("""
                UPDATE major SET major_code=:code, major_name=:name, enabled=:enabled WHERE id=:id
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("code", command.majorCode())
                .addValue("name", command.majorName()).addValue("enabled", command.enabled() ? 1 : 0));
        auditService.success(operator, "MAJOR_UPDATE", "MAJOR", id, null, before, command);
        return id;
    }

    public Map<String, Object> students(
            String keyword,
            String gender,
            Long majorId,
            int page,
            int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        String keywordPattern = keyword == null || keyword.isBlank()
                ? null
                : "%" + keyword.trim() + "%";
        String genderFilter = gender == null || gender.isBlank() ? null : gender;
        StudentCatalogQuery query = new StudentCatalogQuery(
                keywordPattern,
                genderFilter,
                majorId,
                safeSize,
                (safePage - 1) * safeSize);
        int total = Math.toIntExact(studentAdminMapper.countStudents(query));
        List<Map<String, Object>> items = studentAdminMapper.findStudents(query).stream()
                .map(StudentCatalogRow::asResponseMap)
                .toList();
        return Map.of(
                "page", safePage,
                "size", safeSize,
                "total", total,
                "items", items);
    }

    @Transactional
    public long saveStudent(Long id, StudentCommand command, CurrentUser operator) {
        ensureMajorEnabled(command.majorId());
        if (id == null) {
            GeneratedKeyHolder studentKey = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO student (student_number, student_name, gender, major_id)
                    VALUES (:number, :name, :gender, :majorId)
                    """, new MapSqlParameterSource()
                    .addValue("number", command.studentNumber())
                    .addValue("name", command.studentName())
                    .addValue("gender", command.gender())
                    .addValue("majorId", command.majorId()), studentKey, new String[]{"id"});
            long studentId = studentKey.getKey().longValue();
            jdbc.update("""
                    INSERT INTO app_user
                    (student_id, username, password_hash, user_type, account_status, display_name)
                    VALUES (:studentId, :username, NULL, 'STUDENT', 'PENDING', :displayName)
                    """, new MapSqlParameterSource().addValue("studentId", studentId)
                    .addValue("username", command.studentNumber()).addValue("displayName", command.studentName()));
            auditService.success(operator, "STUDENT_CREATE", "STUDENT", studentId, null, null, command);
            return studentId;
        }
        Map<String, Object> before = one("SELECT * FROM student WHERE id=:id", Map.of("id", id), "STUDENT_NOT_FOUND", "学生不存在");
        jdbc.update("""
                UPDATE student SET student_number=:number, student_name=:name,
                    gender=:gender, major_id=:majorId WHERE id=:id
                """, new MapSqlParameterSource().addValue("id", id)
                .addValue("number", command.studentNumber()).addValue("name", command.studentName())
                .addValue("gender", command.gender()).addValue("majorId", command.majorId()));
        jdbc.update("UPDATE app_user SET username=:number, display_name=:name WHERE student_id=:id",
                new MapSqlParameterSource().addValue("id", id).addValue("number", command.studentNumber())
                        .addValue("name", command.studentName()));
        auditService.success(operator, "STUDENT_UPDATE", "STUDENT", id, null, before, command);
        return id;
    }

    @Transactional
    public Map<String, Object> importStudents(List<StudentCommand> commands, CurrentUser operator) {
        int success = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            StudentCommand command = commands.get(index);
            try {
                List<Long> existing = jdbc.query("SELECT id FROM student WHERE student_number=:number",
                        Map.of("number", command.studentNumber()), (rs, rowNum) -> rs.getLong(1));
                saveStudent(existing.isEmpty() ? null : existing.getFirst(), command, operator);
                success++;
            } catch (RuntimeException exception) {
                errors.add(Map.of("row", index + 1, "studentNumber", command.studentNumber(),
                        "message", exception.getMessage() == null ? "导入失败" : exception.getMessage()));
            }
        }
        auditService.success(operator, "STUDENT_IMPORT", "STUDENT", null,
                "批量导入", null, Map.of("total", commands.size(), "success", success, "failed", errors.size()));
        return Map.of("total", commands.size(), "success", success, "failed", errors.size(), "errors", errors);
    }

    public List<Map<String, Object>> buildings() {
        return adminCatalogMapper.findBuildings().stream()
                .map(BuildingCatalogRow::asResponseMap)
                .toList();
    }


    @Transactional
    public void updateRoom(long roomId, RoomCommand command, CurrentUser operator) {
        Map<String, Object> before = one("SELECT * FROM room WHERE id=:id", Map.of("id", roomId), "ROOM_NOT_FOUND", "房间不存在");
        int bedCount = count("SELECT COUNT(*) FROM bed WHERE room_id=:id AND operational_status='ENABLED'", Map.of("id", roomId));
        if (command.capacity() != bedCount) {
            throw new BusinessException("ROOM_CAPACITY_MISMATCH", "房间容量必须等于当前启用床位数量");
        }
        jdbc.update("""
                UPDATE room SET room_type=:roomType, capacity=:capacity,
                    gender_restriction=:gender, operational_status=:status,
                    remark=:remark, state_version=state_version+1 WHERE id=:id
                """, new MapSqlParameterSource().addValue("id", roomId)
                .addValue("roomType", command.roomType()).addValue("capacity", command.capacity())
                .addValue("gender", command.gender()).addValue("status", command.operationalStatus())
                .addValue("remark", command.remark(), Types.VARCHAR));
        auditService.success(operator, "ROOM_UPDATE", "ROOM", roomId, command.reason(), before, command);
    }

    public List<Map<String, Object>> batches() {
        return jdbc.queryForList("""
                SELECT sb.*,
                       (SELECT COUNT(*) FROM batch_student_eligibility e WHERE e.batch_id=sb.id AND e.eligibility_status='ELIGIBLE') AS eligible_count,
                       (SELECT COUNT(*) FROM bed_assignment a WHERE a.batch_id=sb.id) AS assigned_count
                FROM selection_batch sb ORDER BY sb.created_at DESC
                """, Map.of());
    }

    @Transactional
    public long createBatch(BatchCommand command, CurrentUser operator) {
        Map<String, Object> questionnaire = one("""
                SELECT id FROM questionnaire_version WHERE version_status='PUBLISHED'
                ORDER BY published_at DESC LIMIT 1
                """, Map.of(), "QUESTIONNAIRE_REQUIRED", "请先发布生活习惯问卷");
        Map<String, Object> scheme = one("""
                SELECT id FROM matching_weight_scheme WHERE enabled=1 ORDER BY id LIMIT 1
                """, Map.of(), "WEIGHT_SCHEME_REQUIRED", "请先配置匹配权重方案");
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO selection_batch
                (batch_code, batch_name, batch_status, questionnaire_version_id,
                 matching_weight_scheme_id, start_at, end_at, hold_duration_seconds,
                 hold_renewal_limit, allow_team, team_min_size, team_max_size,
                 allow_student_random, unselected_strategy, rule_version, created_by)
                VALUES
                (:code, :name, 'DRAFT', :questionnaireId, :schemeId, :startAt, :endAt,
                 :holdSeconds, 1, :allowTeam, 2, :teamMaxSize,
                 :allowRandom, 'ADMIN_ALLOCATION', 'phase1-rule-v1', :createdBy)
                """, new MapSqlParameterSource().addValue("code", command.batchCode())
                .addValue("name", command.batchName()).addValue("questionnaireId", questionnaire.get("id"))
                .addValue("schemeId", scheme.get("id")).addValue("startAt", command.startAt())
                .addValue("endAt", command.endAt()).addValue("holdSeconds", command.holdDurationSeconds())
                .addValue("allowTeam", command.allowTeam() ? 1 : 0).addValue("teamMaxSize", command.teamMaxSize())
                .addValue("allowRandom", command.allowStudentRandom() ? 1 : 0).addValue("createdBy", operator.userId()),
                keyHolder, new String[]{"id"});
        long batchId = keyHolder.getKey().longValue();
        auditService.success(operator, "BATCH_CREATE", "SELECTION_BATCH", batchId, null, null, command);
        return batchId;
    }

    @Transactional
    public Map<String, Object> prepareBatch(long batchId, CurrentUser operator) {
        ensureBatchStatus(batchId, "DRAFT");
        int students = jdbc.update("""
                INSERT IGNORE INTO batch_student_eligibility (batch_id, student_id, eligibility_status)
                SELECT :batchId, s.id, 'ELIGIBLE' FROM student s JOIN major m ON m.id=s.major_id WHERE m.enabled=1
                """, Map.of("batchId", batchId));
        int buildings = jdbc.update("""
                INSERT IGNORE INTO batch_building_scope (batch_id, building_id)
                SELECT :batchId, id FROM dormitory_building WHERE enabled=1
                """, Map.of("batchId", batchId));
        auditService.success(operator, "BATCH_PREPARE", "SELECTION_BATCH", batchId, null, null,
                Map.of("studentRows", students, "buildingRows", buildings));
        return Map.of("addedStudents", students, "addedBuildings", buildings);
    }

    @Transactional
    public void changeBatchStatus(long batchId, String targetStatus, CurrentUser operator) {
        Map<String, Object> before = one("SELECT * FROM selection_batch WHERE id=:id", Map.of("id", batchId), "BATCH_NOT_FOUND", "选寝批次不存在");
        String current = String.valueOf(before.get("batch_status"));
        if (!BATCH_TRANSITIONS.getOrDefault(current, Set.of()).contains(targetStatus)) {
            throw new BusinessException("BATCH_STATUS_INVALID", "不允许从" + current + "切换到" + targetStatus);
        }
        if (Set.of("PUBLISHED", "OPEN").contains(targetStatus)) {
            int eligible = count("SELECT COUNT(*) FROM batch_student_eligibility WHERE batch_id=:id AND eligibility_status='ELIGIBLE'", Map.of("id", batchId));
            int scopes = count("SELECT COUNT(*) FROM batch_building_scope WHERE batch_id=:id", Map.of("id", batchId))
                    + count("SELECT COUNT(*) FROM batch_room_scope WHERE batch_id=:id", Map.of("id", batchId));
            if (eligible == 0 || scopes == 0) {
                throw new BusinessException("BATCH_NOT_READY", "发布或开放前必须配置学生资格和宿舍范围");
            }
        }
        jdbc.update("""
                UPDATE selection_batch SET batch_status=:status,
                    published_at=CASE WHEN :status='PUBLISHED' THEN CURRENT_TIMESTAMP(3) ELSE published_at END
                WHERE id=:id
                """, Map.of("id", batchId, "status", targetStatus));
        auditService.success(operator, "BATCH_STATUS_CHANGE", "SELECTION_BATCH", batchId,
                current + " -> " + targetStatus, before, Map.of("batchStatus", targetStatus));
    }

    public Map<String, Object> allocationPreview(long batchId, long randomSeed) {
        return buildAllocation(batchId, randomSeed);
    }

    @Transactional
    public Map<String, Object> allocationCommit(long batchId, long randomSeed, String idempotencyKey,
                                                 CurrentUser operator) {
        Map<String, Object> batch = one("SELECT * FROM selection_batch WHERE id=:id", Map.of("id", batchId), "BATCH_NOT_FOUND", "选寝批次不存在");
        String status = String.valueOf(batch.get("batch_status"));
        if (!Set.of("CLOSED", "ALLOCATING").contains(status)) {
            throw new BusinessException("BATCH_NOT_CLOSED", "仅已关闭或分配中的批次可以执行统一分配");
        }
        List<Map<String, Object>> old = jdbc.queryForList("SELECT id, summary_json FROM allocation_run WHERE batch_id=:batchId AND idempotency_key=:key",
                new MapSqlParameterSource().addValue("batchId", batchId).addValue("key", idempotencyKey));
        if (!old.isEmpty()) {
            return Map.of("allocationRunId", old.getFirst().get("id"), "reused", true,
                    "summary", old.getFirst().get("summary_json"));
        }
        Map<String, Object> preview = buildAllocation(batchId, randomSeed);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) preview.get("assignments");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unassigned = (List<Map<String, Object>>) preview.get("unassigned");
        GeneratedKeyHolder runKey = new GeneratedKeyHolder();
        String executionCode = "ALLOC-" + batchId + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        jdbc.update("""
                INSERT INTO allocation_run
                (batch_id, execution_code, idempotency_key, run_mode, run_status,
                 algorithm_version, rule_version, random_seed, student_snapshot_json,
                 bed_snapshot_json, summary_json, operator_user_id, started_at, finished_at)
                VALUES
                (:batchId, :executionCode, :idempotencyKey, 'COMMIT', :status,
                 'greedy-gender-v1', 'phase1-rule-v1', :seed, CAST(:students AS JSON),
                 CAST(:beds AS JSON), CAST(:summary AS JSON), :operatorId,
                 CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource().addValue("batchId", batchId)
                .addValue("executionCode", executionCode).addValue("idempotencyKey", idempotencyKey)
                .addValue("status", unassigned.isEmpty() ? "SUCCEEDED" : "PARTIAL_SUCCESS")
                .addValue("seed", randomSeed).addValue("students", json(preview.get("students")))
                .addValue("beds", json(preview.get("beds"))).addValue("summary", json(preview.get("summary")))
                .addValue("operatorId", operator.userId()), runKey, new String[]{"id"});
        long runId = runKey.getKey().longValue();
        for (Map<String, Object> item : assignments) {
            long studentId = ((Number) item.get("studentId")).longValue();
            long bedId = ((Number) item.get("bedId")).longValue();
            GeneratedKeyHolder assignmentKey = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO bed_assignment
                    (batch_id, student_id, bed_id, assignment_method, assignment_status,
                     allocation_run_id, assigned_by, assigned_at)
                    VALUES (:batchId, :studentId, :bedId, 'ADMIN_RANDOM', 'ACTIVE',
                            :runId, :operatorId, CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource().addValue("batchId", batchId)
                    .addValue("studentId", studentId).addValue("bedId", bedId)
                    .addValue("runId", runId).addValue("operatorId", operator.userId()),
                    assignmentKey, new String[]{"id"});
            long assignmentId = assignmentKey.getKey().longValue();
            jdbc.update("""
                    INSERT INTO assignment_history
                    (assignment_id, batch_id, student_id, bed_id, event_type,
                     assignment_method, operator_user_id, reason, current_data, occurred_at)
                    VALUES (:assignmentId, :batchId, :studentId, :bedId, 'CREATED',
                            'ADMIN_RANDOM', :operatorId, '统一随机分配', CAST(:currentData AS JSON), CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource().addValue("assignmentId", assignmentId)
                    .addValue("batchId", batchId).addValue("studentId", studentId).addValue("bedId", bedId)
                    .addValue("operatorId", operator.userId()).addValue("currentData", json(item)));
            jdbc.update("""
                    INSERT INTO allocation_run_result
                    (allocation_run_id, student_id, bed_id, result_status, score, explanation_json)
                    VALUES (:runId, :studentId, :bedId, 'ASSIGNED', :score, CAST(:explanation AS JSON))
                    """, new MapSqlParameterSource().addValue("runId", runId).addValue("studentId", studentId)
                    .addValue("bedId", bedId).addValue("score", item.get("score"))
                    .addValue("explanation", json(Map.of("rule", "性别一致、按学生编号稳定排序"))));
        }
        for (Map<String, Object> item : unassigned) {
            jdbc.update("""
                    INSERT INTO allocation_run_result
                    (allocation_run_id, student_id, result_status, failure_code, explanation_json)
                    VALUES (:runId, :studentId, 'UNASSIGNED', 'NO_AVAILABLE_BED', CAST(:explanation AS JSON))
                    """, new MapSqlParameterSource().addValue("runId", runId)
                    .addValue("studentId", item.get("studentId"))
                    .addValue("explanation", json(Map.of("message", "没有符合性别和批次范围的剩余床位"))));
        }
        jdbc.update("UPDATE selection_batch SET batch_status='FINISHED' WHERE id=:id", Map.of("id", batchId));
        auditService.success(operator, "ALLOCATION_COMMIT", "ALLOCATION_RUN", runId,
                "统一分配", null, preview.get("summary"));
        return Map.of("allocationRunId", runId, "executionCode", executionCode,
                "reused", false, "summary", preview.get("summary"));
    }

    public List<Map<String, Object>> auditLogs(int limit) {
        return jdbc.queryForList("""
                SELECT id, request_id, operator_user_id, operator_type, action_type,
                       resource_type, resource_id, result_status, reason, occurred_at
                FROM audit_log ORDER BY occurred_at DESC LIMIT :limit
                """, Map.of("limit", Math.min(Math.max(limit, 1), 500)));
    }

    private Map<String, Object> buildAllocation(long batchId, long randomSeed) {
        ensureBatchExists(batchId);
        List<Map<String, Object>> students = jdbc.queryForList("""
                SELECT s.id AS student_id, s.student_number, s.gender, s.major_id
                FROM batch_student_eligibility e JOIN student s ON s.id=e.student_id
                LEFT JOIN bed_assignment a ON a.batch_id=e.batch_id AND a.student_id=s.id
                WHERE e.batch_id=:batchId AND e.eligibility_status='ELIGIBLE' AND a.id IS NULL
                ORDER BY s.gender, MOD(s.id + :seed, 100000), s.id
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("seed", randomSeed));
        List<Map<String, Object>> beds = jdbc.queryForList("""
                SELECT bed.id AS bed_id, r.id AS room_id, r.gender_restriction AS gender,
                       b.building_name, r.room_number, bed.bed_code
                FROM bed JOIN room r ON r.id=bed.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                LEFT JOIN bed_assignment a ON a.batch_id=:batchId AND a.bed_id=bed.id
                WHERE bed.operational_status='ENABLED' AND r.operational_status='ENABLED' AND a.id IS NULL
                  AND (
                    EXISTS (SELECT 1 FROM batch_room_scope rs WHERE rs.batch_id=:batchId AND rs.room_id=r.id)
                    OR EXISTS (SELECT 1 FROM batch_building_scope bs WHERE bs.batch_id=:batchId AND bs.building_id=b.id)
                  )
                  AND (
                    NOT EXISTS (SELECT 1 FROM batch_bed_scope x WHERE x.batch_id=:batchId)
                    OR EXISTS (SELECT 1 FROM batch_bed_scope x WHERE x.batch_id=:batchId AND x.bed_id=bed.id)
                  )
                ORDER BY r.gender_restriction, MOD(bed.id + :seed, 100000), bed.id
                """, new MapSqlParameterSource().addValue("batchId", batchId).addValue("seed", randomSeed));
        Map<String, List<Map<String, Object>>> bedsByGender = new HashMap<>();
        beds.forEach(bed -> bedsByGender.computeIfAbsent(String.valueOf(bed.get("gender")), ignored -> new ArrayList<>()).add(bed));
        Map<String, Integer> indexes = new HashMap<>();
        List<Map<String, Object>> assignments = new ArrayList<>();
        List<Map<String, Object>> unassigned = new ArrayList<>();
        for (Map<String, Object> student : students) {
            String gender = String.valueOf(student.get("gender"));
            int index = indexes.getOrDefault(gender, 0);
            List<Map<String, Object>> candidates = bedsByGender.getOrDefault(gender, List.of());
            if (index >= candidates.size()) {
                unassigned.add(Map.of("studentId", student.get("student_id"), "studentNumber", student.get("student_number"), "gender", gender));
                continue;
            }
            Map<String, Object> bed = candidates.get(index);
            indexes.put(gender, index + 1);
            assignments.add(Map.of(
                    "studentId", student.get("student_id"),
                    "studentNumber", student.get("student_number"),
                    "bedId", bed.get("bed_id"),
                    "roomId", bed.get("room_id"),
                    "room", bed.get("building_name") + " " + bed.get("room_number") + "-" + bed.get("bed_code"),
                    "score", 100.0
            ));
        }
        Map<String, Object> summary = Map.of("studentCount", students.size(), "availableBedCount", beds.size(),
                "assignedCount", assignments.size(), "unassignedCount", unassigned.size(), "randomSeed", randomSeed);
        return Map.of("students", students, "beds", beds, "assignments", assignments,
                "unassigned", unassigned, "summary", summary);
    }

    private void ensureMajorEnabled(long majorId) {
        if (count("SELECT COUNT(*) FROM major WHERE id=:id AND enabled=1", Map.of("id", majorId)) == 0) {
            throw new BusinessException("MAJOR_NOT_AVAILABLE", "专业不存在或已禁用");
        }
    }

    private void ensureBatchExists(long batchId) {
        one("SELECT id FROM selection_batch WHERE id=:id", Map.of("id", batchId), "BATCH_NOT_FOUND", "选寝批次不存在");
    }

    private void ensureBatchStatus(long batchId, String status) {
        Map<String, Object> batch = one("SELECT batch_status FROM selection_batch WHERE id=:id", Map.of("id", batchId), "BATCH_NOT_FOUND", "选寝批次不存在");
        if (!status.equals(String.valueOf(batch.get("batch_status")))) {
            throw new BusinessException("BATCH_STATUS_INVALID", "当前批次状态不允许执行该操作");
        }
    }

    private int count(String sql, Map<String, ?> parameters) {
        Integer value = jdbc.queryForObject(sql, parameters, Integer.class);
        return value == null ? 0 : value;
    }

    private Map<String, Object> one(String sql, Map<String, ?> parameters, String code, String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    public record MajorCommand(String majorCode, String majorName, boolean enabled) {
    }

    public record StudentCommand(String studentNumber, String studentName, String gender, long majorId) {
    }

    public record RoomCommand(String roomType, int capacity, String gender,
                              String operationalStatus, String remark, String reason) {
    }

    public record BatchCommand(String batchCode, String batchName, LocalDateTime startAt,
                               LocalDateTime endAt, int holdDurationSeconds, boolean allowTeam,
                               int teamMaxSize, boolean allowStudentRandom) {
    }
}
