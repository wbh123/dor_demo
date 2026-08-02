package com.wust.dormitory.admin;

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
import java.util.List;
import java.util.Map;

@Service
public class StudentAdminService {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService auditService;

    public StudentAdminService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    public Map<String, Object> students(
            String keyword,
            String gender,
            Long majorId,
            String studentCategory,
            String enrollmentSource,
            int page,
            int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.student_number LIKE :keyword OR s.student_name LIKE :keyword ")
                    .append("OR s.phone_number LIKE :keyword) ");
            parameters.addValue("keyword", "%" + keyword.trim() + "%");
        }
        if (gender != null && !gender.isBlank()) {
            where.append(" AND s.gender=:gender ");
            parameters.addValue("gender", gender);
        }
        if (majorId != null) {
            where.append(" AND s.major_id=:majorId ");
            parameters.addValue("majorId", majorId);
        }
        if (studentCategory != null && !studentCategory.isBlank()) {
            where.append(" AND s.student_category=:studentCategory ");
            parameters.addValue("studentCategory", studentCategory);
        }
        if (enrollmentSource != null && !enrollmentSource.isBlank()) {
            where.append(" AND s.enrollment_source=:enrollmentSource ");
            parameters.addValue("enrollmentSource", enrollmentSource);
        }
        Integer totalValue = jdbc.queryForObject(
                "SELECT COUNT(*) FROM student s" + where,
                parameters,
                Integer.class);
        parameters.addValue("limit", safeSize)
                .addValue("offset", (safePage - 1) * safeSize);
        List<Map<String, Object>> items = jdbc.queryForList("""
                SELECT s.id, s.student_number, s.student_name, s.gender,
                       s.nationality_code, s.student_category,
                       s.enrollment_source, s.phone_number, s.major_id,
                       m.major_code, m.major_name, u.account_status,
                       EXISTS(
                           SELECT 1 FROM room_assignment ra
                           WHERE ra.student_id=s.id AND ra.assignment_status='ACTIVE'
                       ) AS currently_resident
                FROM student s
                JOIN major m ON m.id=s.major_id
                LEFT JOIN app_user u ON u.student_id=s.id
                """ + where + " ORDER BY s.student_number LIMIT :limit OFFSET :offset", parameters);
        return Map.of(
                "page", safePage,
                "size", safeSize,
                "total", totalValue == null ? 0 : totalValue,
                "items", items);
    }

    @Transactional
    public long saveStudent(Long id, StudentCommand command, CurrentUser operator) {
        validate(command);
        ensureMajorEnabled(command.majorId());
        if (id == null) {
            GeneratedKeyHolder studentKey = new GeneratedKeyHolder();
            jdbc.update("""
                    INSERT INTO student
                    (student_number, student_name, gender, major_id,
                     nationality_code, student_category, enrollment_source,
                     phone_number)
                    VALUES (:number, :name, :gender, :majorId,
                            :nationalityCode, :studentCategory,
                            :enrollmentSource, :phoneNumber)
                    """, parameters(command), studentKey, new String[]{"id"});
            long studentId = studentKey.getKey().longValue();
            jdbc.update("""
                    INSERT INTO app_user
                    (student_id, username, password_hash, user_type,
                     account_status, display_name)
                    VALUES (:studentId, :username, NULL, 'STUDENT',
                            'PENDING', :displayName)
                    """, new MapSqlParameterSource()
                    .addValue("studentId", studentId)
                    .addValue("username", command.studentNumber())
                    .addValue("displayName", command.studentName()));
            auditService.success(
                    operator,
                    "STUDENT_CREATE",
                    "STUDENT",
                    studentId,
                    "学生资料录入",
                    null,
                    command);
            return studentId;
        }

        Map<String, Object> before = one(id);
        MapSqlParameterSource update = parameters(command).addValue("id", id);
        jdbc.update("""
                UPDATE student
                SET student_number=:number,
                    student_name=:name,
                    gender=:gender,
                    major_id=:majorId,
                    nationality_code=:nationalityCode,
                    student_category=:studentCategory,
                    enrollment_source=:enrollmentSource,
                    phone_number=:phoneNumber
                WHERE id=:id
                """, update);
        jdbc.update("""
                UPDATE app_user
                SET username=:number, display_name=:name
                WHERE student_id=:id
                """, update);
        auditService.success(
                operator,
                "STUDENT_UPDATE",
                "STUDENT",
                id,
                "学生资料修改",
                before,
                command);
        return id;
    }

    @Transactional
    public Map<String, Object> importStudents(
            List<StudentCommand> commands,
            CurrentUser operator) {
        int success = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        for (int index = 0; index < commands.size(); index++) {
            StudentCommand command = commands.get(index);
            try {
                List<Long> existing = jdbc.query(
                        "SELECT id FROM student WHERE student_number=:number",
                        Map.of("number", command.studentNumber()),
                        (rs, rowNum) -> rs.getLong(1));
                StudentCommand importCommand = command.withEnrollmentSource("BATCH_IMPORT");
                saveStudent(existing.isEmpty() ? null : existing.getFirst(), importCommand, operator);
                success++;
            } catch (RuntimeException exception) {
                errors.add(Map.of(
                        "row", index + 1,
                        "studentNumber", command.studentNumber(),
                        "message", exception.getMessage() == null
                                ? "导入失败"
                                : exception.getMessage()));
            }
        }
        auditService.success(
                operator,
                "STUDENT_IMPORT",
                "STUDENT",
                null,
                "批量导入",
                null,
                Map.of(
                        "total", commands.size(),
                        "success", success,
                        "failed", errors.size()));
        return Map.of(
                "total", commands.size(),
                "success", success,
                "failed", errors.size(),
                "errors", errors);
    }

    private MapSqlParameterSource parameters(StudentCommand command) {
        return new MapSqlParameterSource()
                .addValue("number", command.studentNumber())
                .addValue("name", command.studentName())
                .addValue("gender", command.gender())
                .addValue("majorId", command.majorId())
                .addValue("nationalityCode", command.nationalityCode())
                .addValue("studentCategory", command.studentCategory())
                .addValue("enrollmentSource", command.enrollmentSource())
                .addValue("phoneNumber", command.phoneNumber());
    }

    private void validate(StudentCommand command) {
        if (command.studentNumber() == null || !command.studentNumber().matches("^\\d{12}$")) {
            throw new BusinessException("STUDENT_NUMBER_INVALID", "学号必须为12位数字");
        }
        if (command.studentName() == null || command.studentName().isBlank()) {
            throw new BusinessException("STUDENT_NAME_REQUIRED", "学生姓名不能为空");
        }
        if (!List.of("M", "F").contains(command.gender())) {
            throw new BusinessException("STUDENT_GENDER_INVALID", "学生性别必须为男或女");
        }
        if (command.nationalityCode() == null || !command.nationalityCode().matches("^[A-Z]{2}$")) {
            throw new BusinessException("NATIONALITY_CODE_INVALID", "国籍代码必须为两位大写字母");
        }
        if (!List.of("DOMESTIC", "INTERNATIONAL").contains(command.studentCategory())) {
            throw new BusinessException("STUDENT_CATEGORY_INVALID", "学生类别必须为国内生或国际生");
        }
        if (!List.of("INITIAL_IMPORT", "TRANSFER_MANUAL", "ADMIN_MANUAL", "BATCH_IMPORT")
                .contains(command.enrollmentSource())) {
            throw new BusinessException("ENROLLMENT_SOURCE_INVALID", "学生录入来源不合法");
        }
        if (command.phoneNumber() != null
                && !command.phoneNumber().matches("^\\+?[0-9][0-9 -]{5,30}$")) {
            throw new BusinessException("PHONE_NUMBER_INVALID", "手机号码格式不正确");
        }
    }

    private void ensureMajorEnabled(long majorId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM major WHERE id=:id AND enabled=1",
                Map.of("id", majorId),
                Integer.class);
        if (count == null || count == 0) {
            throw new BusinessException("MAJOR_NOT_AVAILABLE", "专业不存在或已禁用");
        }
    }

    private Map<String, Object> one(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM student WHERE id=:id",
                Map.of("id", id));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "STUDENT_NOT_FOUND",
                    "学生不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    public record StudentCommand(
            String studentNumber,
            String studentName,
            String gender,
            long majorId,
            String nationalityCode,
            String studentCategory,
            String enrollmentSource,
            String phoneNumber) {

        public StudentCommand withEnrollmentSource(String value) {
            return new StudentCommand(
                    studentNumber,
                    studentName,
                    gender,
                    majorId,
                    nationalityCode,
                    studentCategory,
                    value,
                    phoneNumber);
        }
    }
}
