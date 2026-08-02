package com.wust.dormitory.auth;

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
    private static final String DEFAULT_MESSAGE =
            "欢迎加入武汉科技大学宿舍智能选择系统。请先完善个人偏好，再选择合适的宿舍与床位。";

    private final NamedParameterJdbcTemplate jdbc;

    public StudentWelcomeService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public WelcomeData welcomeFor(CurrentUser user) {
        if (user == null || !"STUDENT".equals(user.userType())) {
            return null;
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT welcome_acknowledged_at
                FROM app_user
                WHERE id=:userId
                """, Map.of("userId", user.userId()));
        if (rows.isEmpty()) {
            return null;
        }
        String message = jdbc.query("""
                SELECT setting_value
                FROM system_setting
                WHERE setting_key=:settingKey
                """, Map.of("settingKey", STUDENT_WELCOME_MESSAGE),
                resultSet -> resultSet.next() ? resultSet.getString(1) : DEFAULT_MESSAGE);
        if (message == null || message.isBlank()) {
            message = DEFAULT_MESSAGE;
        }

        WelcomeData data = new WelcomeData();
        data.setRequired(rows.getFirst().get("welcome_acknowledged_at") == null);
        data.setTitle("新同学，欢迎你");
        data.setMessage(message.trim());
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
                SET welcome_acknowledged_at=COALESCE(welcome_acknowledged_at, CURRENT_TIMESTAMP(3))
                WHERE id=:userId AND user_type='STUDENT'
                """, Map.of("userId", user.userId()));
    }
}
