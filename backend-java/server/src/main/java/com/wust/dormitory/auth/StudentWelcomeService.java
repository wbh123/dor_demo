package com.wust.dormitory.auth;

import com.wust.dormitory.admin.CountryRegionCatalog;
import com.wust.dormitory.admin.SystemSettingService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.model.dto.WelcomeData;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentWelcomeService {
    static final String STUDENT_WELCOME_MESSAGE = "STUDENT_WELCOME_MESSAGE";

    private final NamedParameterJdbcTemplate jdbc;
    private final SystemSettingService settingService;

    public StudentWelcomeService(
            NamedParameterJdbcTemplate jdbc,
            SystemSettingService settingService) {
        this.jdbc = jdbc;
        this.settingService = settingService;
    }

    public WelcomeData welcomeFor(CurrentUser user) {
        if (user == null || !"STUDENT".equals(user.userType())) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.welcome_acknowledged_at,
                       s.student_number, s.student_name, s.nationality_code,
                       s.grade_year, s.degree_level,
                       m.major_name
                FROM app_user u
                LEFT JOIN student s ON s.id=u.student_id
                LEFT JOIN major m ON m.id=s.major_id
                WHERE u.id=:userId
                """, Map.of("userId", user.userId()));
        if (rows.isEmpty()) return null;

        Map<String, Object> student = rows.getFirst();
        String rawValue = jdbc.query(
                "SELECT setting_value FROM system_setting WHERE setting_key=:settingKey",
                Map.of("settingKey", STUDENT_WELCOME_MESSAGE),
                resultSet -> resultSet.next() ? resultSet.getString(1) : null);
        SystemSettingService.WelcomeConfiguration configuration =
                settingService.readConfiguration(rawValue);
        String countryCode = String.valueOf(
                student.getOrDefault("nationality_code", "")).toUpperCase();
        Map<String, String> variables = variables(student, countryCode);
        String countryTemplate = configuration.countryMessages().get(countryCode);

        Map<String, String> renderedMessages = new LinkedHashMap<>();
        configuration.messages().forEach((locale, message) ->
                renderedMessages.put(
                        locale,
                        render(countryTemplate == null || countryTemplate.isBlank() ? message : countryTemplate, variables)));

        WelcomeData data = new WelcomeData();
        data.setRequired(student.get("welcome_acknowledged_at") == null);
        data.setTitle("新同学，欢迎你");
        data.setMessages(renderedMessages);
        return data;
    }

    public void acknowledge(CurrentUser user) {
        if (user == null || !"STUDENT".equals(user.userType())) {
            throw new BusinessException(
                    "STUDENT_WELCOME_FORBIDDEN",
                    "只有学生账号可以确认欢迎信息",
                    HttpStatus.FORBIDDEN);
        }
        jdbc.update("""
                UPDATE app_user
                SET welcome_acknowledged_at=COALESCE(
                    welcome_acknowledged_at,
                    CURRENT_TIMESTAMP(3))
                WHERE id=:userId AND user_type='STUDENT'
                """, Map.of("userId", user.userId()));
    }

    private Map<String, String> variables(
            Map<String, Object> student,
            String countryCode) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("学生姓名", text(student.get("student_name"), "同学"));
        variables.put("学号", text(student.get("student_number"), "未填写"));
        variables.put("专业名称", text(student.get("major_name"), "未填写"));
        variables.put("年级", text(student.get("grade_year"), "未填写"));
        variables.put("培养层次", degreeLabel(student.get("degree_level")));
        variables.put("国家或地区", CountryRegionCatalog.name(countryCode));
        return variables;
    }

    private String render(String template, Map<String, String> variables) {
        String rendered = template == null ? "" : template;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            rendered = rendered.replace(
                    "{{" + variable.getKey() + "}}",
                    variable.getValue());
        }
        return rendered;
    }

    private String degreeLabel(Object value) {
        return switch (String.valueOf(value == null ? "" : value)) {
            case "UNDERGRADUATE" -> "本科生";
            case "MASTER" -> "硕士生";
            case "DOCTOR" -> "博士生";
            case "MASTER_DOCTOR" -> "硕博生";
            default -> "未填写";
        };
    }

    private String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }
}
