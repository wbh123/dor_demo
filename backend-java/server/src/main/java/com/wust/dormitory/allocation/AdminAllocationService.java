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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminAllocationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public AdminAllocationService(NamedParameterJdbcTemplate jdbc,
                                  ObjectMapper objectMapper,
                                  AuditService auditService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public Map<String, Object> preview(long batchId, long randomSeed) {
        ensureBatchExists(batchId);
        return plan(batchId, randomSeed).toMap();
    }

    @Transactional
    public Map<String, Object> commit(long batchId,
                                      long randomSeed,
                                      String idempotencyKey,
                                      CurrentUser operator) {
        Map<String, Object> batch = one(
                "SELECT batch_status FROM selection_batch WHERE id=:id",
                Map.of("id", batchId),
                "BATCH_NOT_FOUND",
                "选寝批次不存在"
        );
        String status = String.valueOf(batch.get("batch_status"));
        if (!Set.of("CLOSED", "ALLOCATING").contains(status)) {
            throw new BusinessException(
                    "BATCH_NOT_CLOSED",
                    "仅已关闭或分配中的批次可以执行统一分配"
            );
        }

        List<Map<String, Object>> existing = jdbc.queryForList("""
                SELECT id, execution_code, summary_json
                FROM allocation_run
                WHERE batch_id=:batchId AND idempotency_key=:idempotencyKey
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("idempotencyKey", idempotencyKey));
        if (!existing.isEmpty()) {
            Map<String, Object> row = existing.getFirst();
            Map<String, Object> reused = new LinkedHashMap<>();
            reused.put("allocationRunId", row.get("id"));
            reused.put("executionCode", row.get("execution_code"));
            reused.put("reused", true);
            reused.put("summary", row.get("summary_json"));
            return reused;
        }

        AllocationPlan plan = plan(batchId, randomSeed);
        String executionCode = "ALLOC-" + batchId + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        GeneratedKeyHolder runKey = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO allocation_run
                (batch_id, execution_code, idempotency_key, run_mode, run_status,
                 algorithm_version, rule_version, random_seed,
                 student_snapshot_json, bed_snapshot_json, summary_json,
                 operator_user_id, started_at, finished_at)
                VALUES
                (:batchId, :executionCode, :idempotencyKey, 'COMMIT', :runStatus,
                 'team-first-greedy-v1', 'phase1-rule-v1', :randomSeed,
                 CAST(:studentSnapshot AS JSON), CAST(:bedSnapshot AS JSON),
                 CAST(:summary AS JSON), :operatorId,
                 CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("executionCode", executionCode)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("runStatus", plan.unassigned().isEmpty()
                        ? "SUCCEEDED" : "PARTIAL_SUCCESS")
                .addValue("randomSeed", randomSeed)
                .addValue("studentSnapshot", json(plan.studentSnapshot()))
                .addValue("bedSnapshot", json(plan.bedSnapshot()))
                .addValue("summary", json(plan.summary()))
                .addValue("operatorId", operator.userId()),
                runKey,
                new String[]{"id"});
        long runId = runKey.getKey().longValue();

        Set<Long> completedTeams = new HashSet<>();
        for (AssignmentItem item : plan.assignments()) {
            GeneratedKeyHolder assignmentKey = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO bed_assignment
                    (batch_id, student_id, bed_id, team_id,
                     assignment_method, assignment_status,
                     allocation_run_id, assigned_by, assigned_at)
                    VALUES
                    (:batchId, :studentId, :bedId, :teamId,
                     'ADMIN_RANDOM', 'ACTIVE', :runId, :operatorId,
                     CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource()
                    .addValue("batchId", batchId)
                    .addValue("studentId", item.studentId())
                    .addValue("bedId", item.bedId())
                    .addValue("teamId", item.teamId())
                    .addValue("runId", runId)
                    .addValue("operatorId", operator.userId()),
                    assignmentKey,
                    new String[]{"id"});
            long assignmentId = assignmentKey.getKey().longValue();

            jdbc.update("""
                    INSERT INTO assignment_history
                    (assignment_id, batch_id, student_id, bed_id,
                     event_type, assignment_method, operator_user_id,
                     reason, current_data, occurred_at)
                    VALUES
                    (:assignmentId, :batchId, :studentId, :bedId,
                     'CREATED', 'ADMIN_RANDOM', :operatorId,
                     :reason, CAST(:currentData AS JSON), CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource()
                    .addValue("assignmentId", assignmentId)
                    .addValue("batchId", batchId)
                    .addValue("studentId", item.studentId())
                    .addValue("bedId", item.bedId())
                    .addValue("operatorId", operator.userId())
                    .addValue("reason", item.teamId() == null
                            ? "统一随机分配未组队学生"
                            : "统一随机分配锁定队伍")
                    .addValue("currentData", json(item.toMap())));

            jdbc.update("""
                    INSERT INTO allocation_run_result
                    (allocation_run_id, student_id, bed_id,
                     result_status, score, explanation_json)
                    VALUES
                    (:runId, :studentId, :bedId,
                     'ASSIGNED', :score, CAST(:explanation AS JSON))
                    """, new MapSqlParameterSource()
                    .addValue("runId", runId)
                    .addValue("studentId", item.studentId())
                    .addValue("bedId", item.bedId())
                    .addValue("score", item.score())
                    .addValue("explanation", json(Map.of(
                            "algorithm", "team-first-greedy-v1",
                            "teamPreserved", item.teamId() != null,
                            "genderMatched", true
                    ))));
            if (item.teamId() != null) {
                completedTeams.add(item.teamId());
            }
        }

        for (Long teamId : completedTeams) {
            jdbc.update("""
                    UPDATE selection_team
                    SET team_status='COMPLETED'
                    WHERE id=:teamId AND team_status='LOCKED'
                    """, Map.of("teamId", teamId));
        }

        for (UnassignedItem item : plan.unassigned()) {
            jdbc.update("""
                    INSERT INTO allocation_run_result
                    (allocation_run_id, student_id, result_status,
                     failure_code, explanation_json)
                    VALUES
                    (:runId, :studentId, 'UNASSIGNED',
                     :failureCode, CAST(:explanation AS JSON))
                    """, new MapSqlParameterSource()
                    .addValue("runId", runId)
                    .addValue("studentId", item.studentId())
                    .addValue("failureCode", item.failureCode())
                    .addValue("explanation", json(Map.of(
                            "message", item.message()
                    ))));
        }

        jdbc.update("""
                UPDATE selection_batch
                SET batch_status='FINISHED'
                WHERE id=:batchId
                """, Map.of("batchId", batchId));
        auditService.success(
                operator,
                "ALLOCATION_COMMIT",
                "ALLOCATION_RUN",
                runId,
                "锁定队伍优先，未组队学生随后分配",
                null,
                plan.summary()
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("allocationRunId", runId);
        result.put("executionCode", executionCode);
        result.put("reused", false);
        result.put("summary", plan.summary());
        return result;
    }

    private AllocationPlan plan(long batchId, long randomSeed) {
        List<BedCandidate> beds = availableBeds(batchId, randomSeed);
        List<Map<String, Object>> studentSnapshot = eligibleStudentSnapshot(batchId);
        List<Map<String, Object>> bedSnapshot = beds.stream()
                .map(BedCandidate::toMap)
                .toList();
        Set<Long> usedBeds = new HashSet<>();
        Set<Long> plannedStudents = new HashSet<>();
        List<AssignmentItem> assignments = new ArrayList<>();
        List<UnassignedItem> unassigned = new ArrayList<>();

        Map<Long, List<BedCandidate>> bedsByRoom = new LinkedHashMap<>();
        for (BedCandidate bed : beds) {
            bedsByRoom.computeIfAbsent(bed.roomId(), ignored -> new ArrayList<>())
                    .add(bed);
        }

        List<TeamCandidate> teams = lockedTeams(batchId, randomSeed);
        for (TeamCandidate team : teams) {
            List<StudentCandidate> members = teamMembers(team.teamId());
            if (members.size() != team.memberCount()) {
                members.forEach(member -> unassigned.add(new UnassignedItem(
                        member.studentId(),
                        member.studentNumber(),
                        "TEAM_MEMBER_STATE_INVALID",
                        "队伍成员状态不完整，未执行整体分配"
                )));
                continue;
            }
            List<BedCandidate> selectedRoomBeds = bedsByRoom.values().stream()
                    .filter(roomBeds -> !roomBeds.isEmpty())
                    .filter(roomBeds -> roomBeds.getFirst().gender().equals(team.gender()))
                    .map(roomBeds -> roomBeds.stream()
                            .filter(bed -> !usedBeds.contains(bed.bedId()))
                            .toList())
                    .filter(roomBeds -> roomBeds.size() >= members.size())
                    .findFirst()
                    .orElse(List.of());
            if (selectedRoomBeds.isEmpty()) {
                members.forEach(member -> unassigned.add(new UnassignedItem(
                        member.studentId(),
                        member.studentNumber(),
                        "TEAM_ROOM_CAPACITY_INSUFFICIENT",
                        "没有同一房间可容纳完整锁定队伍"
                )));
                continue;
            }
            for (int index = 0; index < members.size(); index++) {
                StudentCandidate member = members.get(index);
                BedCandidate bed = selectedRoomBeds.get(index);
                usedBeds.add(bed.bedId());
                plannedStudents.add(member.studentId());
                assignments.add(new AssignmentItem(
                        member.studentId(),
                        member.studentNumber(),
                        bed.bedId(),
                        bed.roomId(),
                        bed.displayName(),
                        team.teamId(),
                        100.0
                ));
            }
        }

        List<StudentCandidate> individualStudents = individualStudents(batchId, randomSeed);
        Map<String, List<BedCandidate>> remainingBedsByGender = new HashMap<>();
        for (BedCandidate bed : beds) {
            if (!usedBeds.contains(bed.bedId())) {
                remainingBedsByGender
                        .computeIfAbsent(bed.gender(), ignored -> new ArrayList<>())
                        .add(bed);
            }
        }
        Map<String, Integer> genderIndex = new HashMap<>();
        for (StudentCandidate student : individualStudents) {
            if (plannedStudents.contains(student.studentId())) {
                continue;
            }
            List<BedCandidate> genderBeds = remainingBedsByGender
                    .getOrDefault(student.gender(), List.of());
            int index = genderIndex.getOrDefault(student.gender(), 0);
            if (index >= genderBeds.size()) {
                unassigned.add(new UnassignedItem(
                        student.studentId(),
                        student.studentNumber(),
                        "NO_AVAILABLE_BED",
                        "没有符合性别和批次范围的剩余床位"
                ));
                continue;
            }
            BedCandidate bed = genderBeds.get(index);
            genderIndex.put(student.gender(), index + 1);
            usedBeds.add(bed.bedId());
            plannedStudents.add(student.studentId());
            assignments.add(new AssignmentItem(
                    student.studentId(),
                    student.studentNumber(),
                    bed.bedId(),
                    bed.roomId(),
                    bed.displayName(),
                    null,
                    90.0
            ));
        }

        List<StudentCandidate> activeUnreadyTeamMembers = activeUnreadyTeamMembers(batchId);
        for (StudentCandidate student : activeUnreadyTeamMembers) {
            if (!plannedStudents.contains(student.studentId())) {
                unassigned.add(new UnassignedItem(
                        student.studentId(),
                        student.studentNumber(),
                        "ACTIVE_TEAM_NOT_LOCKED",
                        "学生仍在未锁定队伍中，为避免拆散队伍未进行个人分配"
                ));
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("studentCount", studentSnapshot.size());
        summary.put("availableBedCount", bedSnapshot.size());
        summary.put("lockedTeamCount", teams.size());
        summary.put("assignedCount", assignments.size());
        summary.put("unassignedCount", unassigned.size());
        summary.put("randomSeed", randomSeed);
        summary.put("algorithmVersion", "team-first-greedy-v1");

        return new AllocationPlan(
                studentSnapshot,
                bedSnapshot,
                assignments,
                unassigned,
                summary
        );
    }

    private List<BedCandidate> availableBeds(long batchId, long randomSeed) {
        return jdbc.query("""
                SELECT bed.id AS bed_id,
                       r.id AS room_id,
                       r.gender_restriction AS gender,
                       b.building_name,
                       r.room_number,
                       bed.bed_code
                FROM bed
                JOIN room r ON r.id=bed.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                LEFT JOIN bed_assignment a
                  ON a.batch_id=:batchId AND a.bed_id=bed.id
                WHERE bed.operational_status='ENABLED'
                  AND r.operational_status='ENABLED'
                  AND a.id IS NULL
                  AND (
                    EXISTS (
                      SELECT 1 FROM batch_room_scope rs
                      WHERE rs.batch_id=:batchId AND rs.room_id=r.id
                    )
                    OR EXISTS (
                      SELECT 1 FROM batch_building_scope bs
                      WHERE bs.batch_id=:batchId AND bs.building_id=b.id
                    )
                  )
                  AND (
                    NOT EXISTS (
                      SELECT 1 FROM batch_bed_scope configured
                      WHERE configured.batch_id=:batchId
                    )
                    OR EXISTS (
                      SELECT 1 FROM batch_bed_scope allowed
                      WHERE allowed.batch_id=:batchId AND allowed.bed_id=bed.id
                    )
                  )
                ORDER BY r.gender_restriction,
                         MOD(r.id + :randomSeed, 100000),
                         r.id,
                         bed.position_index
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("randomSeed", randomSeed),
                (rs, rowNum) -> new BedCandidate(
                        rs.getLong("bed_id"),
                        rs.getLong("room_id"),
                        rs.getString("gender"),
                        rs.getString("building_name"),
                        rs.getString("room_number"),
                        rs.getString("bed_code")
                ));
    }

    private List<Map<String, Object>> eligibleStudentSnapshot(long batchId) {
        return jdbc.queryForList("""
                SELECT s.id AS student_id,
                       s.student_number,
                       s.gender,
                       s.major_id
                FROM batch_student_eligibility e
                JOIN student s ON s.id=e.student_id
                LEFT JOIN bed_assignment a
                  ON a.batch_id=e.batch_id AND a.student_id=s.id
                WHERE e.batch_id=:batchId
                  AND e.eligibility_status='ELIGIBLE'
                  AND a.id IS NULL
                ORDER BY s.student_number
                """, Map.of("batchId", batchId));
    }

    private List<TeamCandidate> lockedTeams(long batchId, long randomSeed) {
        return jdbc.query("""
                SELECT t.id AS team_id,
                       MIN(s.gender) AS gender,
                       COUNT(*) AS member_count,
                       COUNT(DISTINCT s.gender) AS gender_count
                FROM selection_team t
                JOIN selection_team_member tm ON tm.team_id=t.id
                JOIN student s ON s.id=tm.student_id
                LEFT JOIN bed_assignment a
                  ON a.batch_id=t.batch_id AND a.student_id=s.id
                WHERE t.batch_id=:batchId
                  AND t.team_status='LOCKED'
                  AND tm.member_status='LOCKED'
                  AND a.id IS NULL
                GROUP BY t.id
                HAVING COUNT(DISTINCT s.gender)=1
                ORDER BY MOD(t.id + :randomSeed, 100000), t.id
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("randomSeed", randomSeed),
                (rs, rowNum) -> new TeamCandidate(
                        rs.getLong("team_id"),
                        rs.getString("gender"),
                        rs.getInt("member_count")
                ));
    }

    private List<StudentCandidate> teamMembers(long teamId) {
        return jdbc.query("""
                SELECT s.id AS student_id,
                       s.student_number,
                       s.gender
                FROM selection_team_member tm
                JOIN student s ON s.id=tm.student_id
                WHERE tm.team_id=:teamId
                  AND tm.member_status='LOCKED'
                ORDER BY tm.member_role='LEADER' DESC, tm.id
                """, Map.of("teamId", teamId),
                (rs, rowNum) -> new StudentCandidate(
                        rs.getLong("student_id"),
                        rs.getString("student_number"),
                        rs.getString("gender")
                ));
    }

    private List<StudentCandidate> individualStudents(long batchId, long randomSeed) {
        return jdbc.query("""
                SELECT s.id AS student_id,
                       s.student_number,
                       s.gender
                FROM batch_student_eligibility e
                JOIN student s ON s.id=e.student_id
                LEFT JOIN bed_assignment a
                  ON a.batch_id=e.batch_id AND a.student_id=s.id
                WHERE e.batch_id=:batchId
                  AND e.eligibility_status='ELIGIBLE'
                  AND a.id IS NULL
                  AND NOT EXISTS (
                    SELECT 1 FROM selection_team_member tm
                    WHERE tm.batch_id=:batchId
                      AND tm.student_id=s.id
                      AND tm.active_marker=1
                  )
                ORDER BY s.gender,
                         MOD(s.id + :randomSeed, 100000),
                         s.id
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("randomSeed", randomSeed),
                (rs, rowNum) -> new StudentCandidate(
                        rs.getLong("student_id"),
                        rs.getString("student_number"),
                        rs.getString("gender")
                ));
    }

    private List<StudentCandidate> activeUnreadyTeamMembers(long batchId) {
        return jdbc.query("""
                SELECT DISTINCT s.id AS student_id,
                       s.student_number,
                       s.gender
                FROM selection_team_member tm
                JOIN selection_team t ON t.id=tm.team_id
                JOIN student s ON s.id=tm.student_id
                LEFT JOIN bed_assignment a
                  ON a.batch_id=tm.batch_id AND a.student_id=s.id
                WHERE tm.batch_id=:batchId
                  AND tm.active_marker=1
                  AND t.team_status<>'LOCKED'
                  AND a.id IS NULL
                ORDER BY s.student_number
                """, Map.of("batchId", batchId),
                (rs, rowNum) -> new StudentCandidate(
                        rs.getLong("student_id"),
                        rs.getString("student_number"),
                        rs.getString("gender")
                ));
    }

    private void ensureBatchExists(long batchId) {
        one(
                "SELECT id FROM selection_batch WHERE id=:id",
                Map.of("id", batchId),
                "BATCH_NOT_FOUND",
                "选寝批次不存在"
        );
    }

    private Map<String, Object> one(String sql,
                                    Map<String, ?> parameters,
                                    String code,
                                    String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    "JSON_ERROR",
                    "分配快照序列化失败",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private record StudentCandidate(long studentId,
                                    String studentNumber,
                                    String gender) {
    }

    private record TeamCandidate(long teamId,
                                 String gender,
                                 int memberCount) {
    }

    private record BedCandidate(long bedId,
                                long roomId,
                                String gender,
                                String buildingName,
                                String roomNumber,
                                String bedCode) {
        String displayName() {
            return buildingName + " " + roomNumber + "-" + bedCode;
        }

        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("bedId", bedId);
            map.put("roomId", roomId);
            map.put("gender", gender);
            map.put("buildingName", buildingName);
            map.put("roomNumber", roomNumber);
            map.put("bedCode", bedCode);
            return map;
        }
    }

    private record AssignmentItem(long studentId,
                                  String studentNumber,
                                  long bedId,
                                  long roomId,
                                  String room,
                                  Long teamId,
                                  double score) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("studentId", studentId);
            map.put("studentNumber", studentNumber);
            map.put("bedId", bedId);
            map.put("roomId", roomId);
            map.put("room", room);
            map.put("teamId", teamId);
            map.put("score", score);
            return map;
        }
    }

    private record UnassignedItem(long studentId,
                                  String studentNumber,
                                  String failureCode,
                                  String message) {
        Map<String, Object> toMap() {
            return Map.of(
                    "studentId", studentId,
                    "studentNumber", studentNumber,
                    "failureCode", failureCode,
                    "message", message
            );
        }
    }

    private record AllocationPlan(List<Map<String, Object>> studentSnapshot,
                                  List<Map<String, Object>> bedSnapshot,
                                  List<AssignmentItem> assignments,
                                  List<UnassignedItem> unassigned,
                                  Map<String, Object> summary) {
        Map<String, Object> toMap() {
            return Map.of(
                    "students", studentSnapshot,
                    "beds", bedSnapshot,
                    "assignments", assignments.stream()
                            .map(AssignmentItem::toMap)
                            .toList(),
                    "unassigned", unassigned.stream()
                            .map(UnassignedItem::toMap)
                            .toList(),
                    "summary", summary
            );
        }
    }
}
