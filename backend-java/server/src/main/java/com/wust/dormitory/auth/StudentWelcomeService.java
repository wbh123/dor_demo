package com.wust.dormitory.auth;

import com.wust.dormitory.admin.SystemSettingService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.model.dto.WelcomeData;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class StudentWelcomeService {
    static final String STUDENT_WELCOME_MESSAGE = "STUDENT_WELCOME_MESSAGE";

    private final NamedParameterJdbcTemplate jdbc;
    private final SystemSettingService settingService;

    public StudentWelcomeService(NamedParameterJdbcTemplate jdbc, SystemSettingService settingService) {
        this.jdbc = jdbc;
        this.settingService = settingService;
    }

    public WelcomeData welcomeFor(CurrentUser user) {
        if (user == null || !"STUDENT".equals(user.userType())) return null;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.welcome_acknowledged_at, s.nationality_code
                FROM app_user u LEFT JOIN student s ON s.id=u.student_id
                WHERE u.id=:userId
                """, Map.of("userId", user.userId()));
        if (rows.isEmpty()) return null;
        String rawValue = jdbc.query("SELECT setting_value FROM system_setting WHERE setting_key=:settingKey",
                Map.of("settingKey", STUDENT_WELCOME_MESSAGE), resultSet -> resultSet.next() ? resultSet.getString(1) : null);
        SystemSettingService.WelcomeConfiguration configuration = settingService.readConfiguration(rawValue);
        String countryCode = String.valueOf(rows.getFirst().getOrDefault("nationality_code", "")).toUpperCase();
        String selected = configuration.countryMessages().get(countryCode);
        if (selected == null || selected.isBlank()) {
            selected = "CN".equals(countryCode)
                    ? configuration.messages().get("zh-CN")
                    : configuration.messages().get("en-US");
        }

        WelcomeData data = new WelcomeData();
        data.setRequired(rows.getFirst().get("welcome_acknowledged_at") == null);
        data.setTitle("新同学，欢迎你");
        data.setMessages(configuration.messages());
        data.setMessage(selected);
        return data;
    }

    public void acknowledge(CurrentUser user) {
        if (user == null || !"STUDENT".equals(user.userType())) {
            throw new BusinessException("STUDENT_WELCOME_FORBIDDEN", "只有学生账号可以确认欢迎信息", HttpStatus.FORBIDDEN);
        }
        jdbc.update("""
                UPDATE app_user SET welcome_acknowledged_at=COALESCE(welcome_acknowledged_at, CURRENT_TIMESTAMP(3))
                WHERE id=:userId AND user_type='STUDENT'
                """, Map.of("userId", user.userId()));
    }
}
