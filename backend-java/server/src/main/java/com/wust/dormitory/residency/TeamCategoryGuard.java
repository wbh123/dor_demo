package com.wust.dormitory.residency;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TeamCategoryGuard {
    private final NamedParameterJdbcTemplate jdbc;

    public TeamCategoryGuard(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireInvitationAllowed(String inviteeStudentNumber, CurrentUser leader) {
        Map<String, Object> context = invitationContext(leader.studentId());
        Map<String, Object> invitee = one("""
                SELECT s.id, s.student_category
                FROM student s
                JOIN batch_student_eligibility e
                  ON e.student_id=s.id AND e.batch_id=:batchId
                WHERE s.student_number=:studentNumber
                  AND e.eligibility_status='ELIGIBLE'
                """, new MapSqlParameterSource()
                .addValue("batchId", context.get("batch_id"))
                .addValue("studentNumber", inviteeStudentNumber)
                .getValues(),
                "INVITEE_NOT_ELIGIBLE",
                "被邀请学生不存在或没有当前批次资格");
        boolean separate = ((Number) context.get("separate_student_categories")).intValue() == 1;
        if (separate && !String.valueOf(context.get("student_category"))
                .equals(String.valueOf(invitee.get("student_category")))) {
            throw new BusinessException(
                    "TEAM_STUDENT_CATEGORY_MISMATCH",
                    "当前批次要求国内生与国际生分开选寝，不能邀请不同类别学生",
                    HttpStatus.CONFLICT);
        }
    }

    public void requireLockAllowed(long teamId, CurrentUser leader) {
        Map<String, Object> team = one("""
                SELECT t.id, t.batch_id, t.leader_student_id,
                       sb.separate_student_categories
                FROM selection_team t
                JOIN selection_batch sb ON sb.id=t.batch_id
                WHERE t.id=:teamId AND t.leader_student_id=:leaderStudentId
                """, new MapSqlParameterSource()
                .addValue("teamId", teamId)
                .addValue("leaderStudentId", leader.studentId())
                .getValues(),
                "TEAM_NOT_FOUND",
                "队伍不存在或你不是队长");
        if (((Number) team.get("separate_student_categories")).intValue() != 1) {
            return;
        }
        Integer categories = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT s.student_category)
                FROM selection_team_member tm
                JOIN student s ON s.id=tm.student_id
                WHERE tm.team_id=:teamId
                  AND tm.member_status IN ('JOINED','LOCKED')
                """, Map.of("teamId", teamId), Integer.class);
        if (categories != null && categories > 1) {
            throw new BusinessException(
                    "TEAM_STUDENT_CATEGORY_MISMATCH",
                    "当前批次要求国内生与国际生分开选寝，混合类别队伍不能锁定",
                    HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> invitationContext(long leaderStudentId) {
        List<Map<String, Object>> formingTeams = jdbc.queryForList("""
                SELECT t.id, t.batch_id, sb.separate_student_categories,
                       s.student_category
                FROM selection_team t
                JOIN selection_batch sb ON sb.id=t.batch_id
                JOIN student s ON s.id=t.leader_student_id
                WHERE t.leader_student_id=:leaderStudentId
                  AND t.team_status='FORMING'
                ORDER BY t.created_at DESC
                LIMIT 1
                """, Map.of("leaderStudentId", leaderStudentId));
        if (!formingTeams.isEmpty()) {
            return formingTeams.getFirst();
        }
        return one("""
                SELECT sb.id AS batch_id, sb.separate_student_categories,
                       s.student_category
                FROM active_batch_student_lock active_lock
                JOIN selection_batch sb ON sb.id=active_lock.batch_id
                JOIN batch_student_eligibility eligibility
                  ON eligibility.batch_id=sb.id
                 AND eligibility.student_id=active_lock.student_id
                JOIN student s ON s.id=active_lock.student_id
                WHERE active_lock.student_id=:leaderStudentId
                  AND eligibility.eligibility_status='ELIGIBLE'
                  AND sb.allow_team=1
                  AND sb.batch_status IN ('PUBLISHED','OPEN')
                ORDER BY sb.start_at DESC, sb.id DESC
                LIMIT 1
                """, Map.of("leaderStudentId", leaderStudentId),
                "TEAM_BATCH_NOT_FOUND",
                "当前没有可用于组队的选寝批次");
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
}
