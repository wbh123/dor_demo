package com.wust.dormitory.student;

import com.wust.dormitory.audit.AuditService;
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
import java.util.UUID;

@Service
public class TeamFormationService {
    private static final int MAX_TEAM_SIZE = 5;

    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public TeamFormationService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> create(CurrentUser user) {
        requireUnassigned(
                user.studentId(),
                "你已经确定寝室或床位，不能创建队伍");
        long batchId = currentBatchId(user.studentId());
        Integer memberships = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM selection_team_member
                WHERE batch_id=:batchId
                  AND student_id=:studentId
                  AND active_marker=1
                """, Map.of(
                "batchId", batchId,
                "studentId", user.studentId()), Integer.class);
        if (memberships != null && memberships > 0) {
            throw new BusinessException(
                    "TEAM_ALREADY_JOINED",
                    "你已经加入当前批次的队伍",
                    HttpStatus.CONFLICT);
        }
        Integer configuredMaximum = jdbc.queryForObject("""
                SELECT team_max_size
                FROM selection_batch
                WHERE id=:batchId
                """, Map.of("batchId", batchId), Integer.class);
        int teamMaximum = configuredMaximum == null
                ? MAX_TEAM_SIZE
                : Math.min(configuredMaximum, MAX_TEAM_SIZE);

        String code = "T" + batchId + "-" + UUID.randomUUID()
                .toString().substring(0, 8).toUpperCase();
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO selection_team
                (batch_id, team_code, team_name, leader_student_id, team_status)
                VALUES (:batchId, :code, :name, :studentId, 'FORMING')
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("code", code)
                .addValue("name", code)
                .addValue("studentId", user.studentId()),
                key,
                new String[]{"id"});
        long teamId = key.getKey().longValue();
        jdbc.update("""
                INSERT INTO selection_team_member
                (team_id, batch_id, student_id, member_role, member_status, joined_at)
                VALUES (:teamId, :batchId, :studentId, 'LEADER', 'JOINED', CURRENT_TIMESTAMP(3))
                """, Map.of(
                "teamId", teamId,
                "batchId", batchId,
                "studentId", user.studentId()));
        auditService.success(
                user,
                "TEAM_CREATE_INTERNAL",
                "SELECTION_TEAM",
                teamId,
                "学生创建组队中的队伍",
                Map.of(),
                Map.of("batchId", batchId));
        return Map.of(
                "id", teamId,
                "batch_id", batchId,
                "team_status", "FORMING",
                "member_role", "LEADER",
                "team_max_size", teamMaximum);
    }

    long currentBatchId(long studentId) {
        List<Long> rows = jdbc.query("""
                SELECT batch_record.id
                FROM active_batch_student_lock active_lock
                JOIN selection_batch batch_record
                  ON batch_record.id=active_lock.batch_id
                JOIN batch_student_eligibility eligibility
                  ON eligibility.batch_id=batch_record.id
                 AND eligibility.student_id=active_lock.student_id
                WHERE active_lock.student_id=:studentId
                  AND eligibility.eligibility_status='ELIGIBLE'
                  AND batch_record.allow_team=1
                  AND batch_record.batch_status IN ('PUBLISHED','OPEN')
                """, Map.of("studentId", studentId),
                (resultSet, rowNumber) -> resultSet.getLong(1));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "TEAM_NOT_AVAILABLE",
                    "当前没有可以组队的选寝活动",
                    HttpStatus.CONFLICT);
        }
        return rows.getFirst();
    }

    void requireUnassigned(long studentId, String message) {
        Integer count = jdbc.queryForObject("""
                SELECT (
                    EXISTS(
                        SELECT 1 FROM room_assignment
                        WHERE student_id=:studentId
                          AND assignment_status='ACTIVE'
                    )
                    OR EXISTS(
                        SELECT 1 FROM bed_assignment
                        WHERE student_id=:studentId
                          AND assignment_status='ACTIVE'
                    )
                )
                """, Map.of("studentId", studentId), Integer.class);
        if (count != null && count > 0) {
            throw new BusinessException(
                    "TEAM_ASSIGNED_FORBIDDEN",
                    message,
                    HttpStatus.CONFLICT);
        }
    }
}
