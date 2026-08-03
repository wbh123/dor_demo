package com.wust.dormitory.student;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class StudentProfileService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public StudentProfileService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public Map<String, Object> profile(CurrentUser user) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id, s.student_number, s.student_name, s.gender,
                       s.nationality_code, s.student_category, s.degree_level, s.grade_year, s.phone_number,
                       m.id AS major_id, m.major_code, m.major_name
                FROM student s
                JOIN major m ON m.id=s.major_id
                WHERE s.id=:studentId
                """, Map.of("studentId", user.studentId()));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "STUDENT_NOT_FOUND",
                    "学生档案不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    @Transactional
    public Map<String, Object> updatePhoneNumber(String phoneNumber, CurrentUser user) {
        String normalized = phoneNumber == null ? "" : phoneNumber.trim();
        if (!normalized.matches("^\\+?[0-9][0-9 -]{5,30}$")) {
            throw new BusinessException(
                    "PHONE_NUMBER_INVALID",
                    "手机号码格式不正确");
        }
        Map<String, Object> before = profile(user);
        jdbc.update("""
                UPDATE student
                SET phone_number=:phoneNumber
                WHERE id=:studentId
                """, new MapSqlParameterSource()
                .addValue("phoneNumber", normalized)
                .addValue("studentId", user.studentId()));
        auditService.success(
                user,
                "STUDENT_PHONE_UPDATE",
                "STUDENT",
                user.studentId(),
                "学生本人修改手机号码",
                Map.of("phoneNumber", before.get("phone_number") == null ? "" : before.get("phone_number")),
                Map.of("phoneNumber", normalized));
        return profile(user);
    }
}
