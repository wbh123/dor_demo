package com.wust.dormitory.residency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class BedResidencySynchronizationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyPolicyService policy;
    private final ObjectMapper objectMapper;

    public BedResidencySynchronizationService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyPolicyService policy,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void synchronizeStudent(long batchId, long studentId, CurrentUser operator) {
        Map<String, Object> assignment = assignment(batchId, studentId);
        synchronizeOne(assignment, operator);
    }

    @Transactional
    public void synchronizeTeam(long batchId, long teamId, CurrentUser operator) {
        List<Map<String, Object>> assignments = jdbc.queryForList("""
                SELECT ba.id AS bed_assignment_id, ba.batch_id, ba.student_id,
                       ba.bed_id, ba.team_id, ba.assignment_method, ba.assigned_at,
                       b.room_id
                FROM bed_assignment ba
                JOIN bed b ON b.id=ba.bed_id
                WHERE ba.batch_id=:batchId AND ba.team_id=:teamId
                  AND ba.assignment_status='ACTIVE'
                ORDER BY ba.id
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("teamId", teamId));
        if (assignments.isEmpty()) {
            throw new BusinessException(
                    "BED_ASSIGNMENT_NOT_FOUND",
                    "队伍床位分配结果不存在",
                    HttpStatus.NOT_FOUND);
        }
        for (Map<String, Object> assignment : assignments) {
            synchronizeOne(assignment, operator);
        }
    }

    private Map<String, Object> assignment(long batchId, long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ba.id AS bed_assignment_id, ba.batch_id, ba.student_id,
                       ba.bed_id, ba.team_id, ba.assignment_method, ba.assigned_at,
                       b.room_id
                FROM bed_assignment ba
                JOIN bed b ON b.id=ba.bed_id
                WHERE ba.batch_id=:batchId AND ba.student_id=:studentId
                  AND ba.assignment_status='ACTIVE'
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "BED_ASSIGNMENT_NOT_FOUND",
                    "学生床位分配结果不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void synchronizeOne(Map<String, Object> assignment, CurrentUser operator) {
        long batchId = number(assignment, "batch_id");
        long studentId = number(assignment, "student_id");
        long roomId = number(assignment, "room_id");
        long bedId = number(assignment, "bed_id");
        Long teamId = assignment.get("team_id") == null
                ? null
                : ((Number) assignment.get("team_id")).longValue();
        List<Map<String, Object>> residencies = jdbc.queryForList("""
                SELECT id, room_id, bed_id, assignment_status
                FROM room_assignment
                WHERE student_id=:studentId AND assignment_status='ACTIVE'
                FOR UPDATE
                """, Map.of("studentId", studentId));
        if (!residencies.isEmpty()) {
            Map<String, Object> existing = residencies.getFirst();
            long existingRoomId = number(existing, "room_id");
            if (existingRoomId != roomId) {
                throw new BusinessException(
                        "STUDENT_ALREADY_RESIDENT",
                        "学生已经归属其他寝室，不能同步当前床位分配",
                        HttpStatus.CONFLICT);
            }
            if (existing.get("bed_id") != null) {
                long existingBedId = number(existing, "bed_id");
                if (existingBedId == bedId) {
                    return;
                }
                throw new BusinessException(
                        "STUDENT_BED_ALREADY_CONFIRMED",
                        "学生已经确认其他实际床位",
                        HttpStatus.CONFLICT);
            }
            policy.requireAvailableBed(roomId, bedId);
            jdbc.update("""
                    UPDATE room_assignment
                    SET bed_id=:bedId,
                        source_selection_mode='BED',
                        assignment_method=:method,
                        team_id=:teamId,
                        bed_confirmed_at=CURRENT_TIMESTAMP(3),
                        updated_at=CURRENT_TIMESTAMP(3)
                    WHERE id=:id
                    """, new MapSqlParameterSource()
                    .addValue("bedId", bedId)
                    .addValue("method", teamId == null ? "BED_SELECT" : "TEAM_BED_SELECT")
                    .addValue("teamId", teamId)
                    .addValue("id", existing.get("id")));
            history(
                    number(existing, "id"), studentId, roomId, bedId,
                    "BED_CONFIRMED", operator.userId(),
                    "选床结果同步为实际床位",
                    existing,
                    Map.of("batchId", batchId, "roomId", roomId, "bedId", bedId));
            return;
        }

        Map<String, Object> student = policy.student(studentId);
        Map<String, Object> batch = policy.batch(batchId);
        Map<String, Object> room = policy.room(roomId, true);
        policy.requireStudentEligibleForRoom(student, batch, room);
        policy.requireRoomCapacity(roomId, 1);
        policy.requireAvailableBed(roomId, bedId);

        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO room_assignment
                (batch_id, student_id, room_id, bed_id, team_id,
                 source_selection_mode, assignment_method, assignment_status,
                 assigned_by, assigned_at, bed_confirmed_at)
                VALUES (:batchId, :studentId, :roomId, :bedId, :teamId,
                        'BED', :method, 'ACTIVE', :operatorId,
                        :assignedAt, :assignedAt)
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId)
                .addValue("roomId", roomId)
                .addValue("bedId", bedId)
                .addValue("teamId", teamId)
                .addValue("method", teamId == null ? "BED_SELECT" : "TEAM_BED_SELECT")
                .addValue("operatorId", operator.userId())
                .addValue("assignedAt", assignment.get("assigned_at")),
                keyHolder,
                new String[]{"id"});
        long residencyId = keyHolder.getKey().longValue();
        history(
                residencyId, studentId, roomId, bedId,
                "BED_ASSIGNED", operator.userId(),
                "选床结果同步为跨批次在住事实",
                null,
                Map.of("batchId", batchId, "roomId", roomId, "bedId", bedId));
    }

    private void history(
            long residencyId,
            long studentId,
            long roomId,
            long bedId,
            String eventType,
            long operatorId,
            String reason,
            Object previous,
            Object current) {
        try {
            jdbc.update("""
                    INSERT INTO room_assignment_history
                    (room_assignment_id, student_id, room_id, bed_id,
                     event_type, operator_user_id, reason,
                     previous_data, current_data, occurred_at)
                    VALUES (:residencyId, :studentId, :roomId, :bedId,
                            :eventType, :operatorId, :reason,
                            CAST(:previousData AS JSON), CAST(:currentData AS JSON),
                            CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource()
                    .addValue("residencyId", residencyId)
                    .addValue("studentId", studentId)
                    .addValue("roomId", roomId)
                    .addValue("bedId", bedId)
                    .addValue("eventType", eventType)
                    .addValue("operatorId", operatorId)
                    .addValue("reason", reason)
                    .addValue("previousData", previous == null
                            ? null : objectMapper.writeValueAsString(previous))
                    .addValue("currentData", objectMapper.writeValueAsString(current)));
        } catch (Exception exception) {
            throw new IllegalStateException("在住同步历史写入失败", exception);
        }
    }

    private long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }
}
