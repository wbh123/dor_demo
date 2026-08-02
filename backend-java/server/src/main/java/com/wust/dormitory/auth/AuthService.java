package com.wust.dormitory.auth;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.AuthTokenService;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokenService;

    public AuthService(NamedParameterJdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                       AuthTokenService tokenService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
    }

    public LoginResult login(String username, String password) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, student_id, username, password_hash, user_type, account_status,
                       display_name, password_change_required
                FROM app_user WHERE username = :username
                """, Map.of("username", username));
        if (rows.isEmpty()) {
            throw new BusinessException("AUTH_INVALID", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        Map<String, Object> row = rows.getFirst();
        if ("SYSTEM_ADMIN".equals(String.valueOf(row.get("user_type")))) {
            throw new BusinessException("PLATFORM_LOGIN_REQUIRED", "请使用系统管理入口登录", HttpStatus.FORBIDDEN);
        }
        String status = String.valueOf(row.get("account_status"));
        String hash = (String) row.get("password_hash");
        if (!"ACTIVE".equals(status) || hash == null || !passwordEncoder.matches(password, hash)) {
            throw new BusinessException("AUTH_INVALID", "用户名或密码错误，未激活学生请先完成激活", HttpStatus.UNAUTHORIZED);
        }
        CurrentUser user = toUser(row);
        AuthTokenService.Token token = tokenService.issue(user);
        jdbc.update("UPDATE app_user SET last_login_at=CURRENT_TIMESTAMP(3) WHERE id=:id", Map.of("id", user.userId()));
        return new LoginResult(token.accessToken(), token.expiresInSeconds(), user);
    }

    private CurrentUser toUser(Map<String, Object> row) {
        Object required = row.get("password_change_required");
        return new CurrentUser(
                ((Number) row.get("id")).longValue(),
                row.get("student_id") == null ? null : ((Number) row.get("student_id")).longValue(),
                String.valueOf(row.get("username")),
                String.valueOf(row.get("display_name")),
                String.valueOf(row.get("user_type")),
                required != null && ((Number) required).intValue() == 1
        );
    }

    @Transactional
    public void activate(String studentNumber, String studentName, String password) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.id AS student_id, s.student_name, u.id AS user_id,
                       u.password_hash, u.account_status
                FROM student s JOIN app_user u ON u.student_id=s.id
                WHERE s.student_number=:studentNumber
                """, Map.of("studentNumber", studentNumber));
        if (rows.isEmpty() || !studentName.equals(String.valueOf(rows.getFirst().get("student_name")))) {
            throw new BusinessException("ACTIVATION_INVALID", "学号或姓名不匹配");
        }
        Map<String, Object> row = rows.getFirst();
        if (row.get("password_hash") != null && "ACTIVE".equals(String.valueOf(row.get("account_status")))) {
            throw new BusinessException("ACCOUNT_ALREADY_ACTIVE", "该学生账号已经激活");
        }
        jdbc.update("""
                UPDATE app_user
                SET password_hash=:passwordHash,
                    account_status='ACTIVE',
                    welcome_acknowledged_at=NULL
                WHERE id=:userId
                """, new MapSqlParameterSource()
                .addValue("passwordHash", passwordEncoder.encode(password))
                .addValue("userId", ((Number) row.get("user_id")).longValue()));
    }

    public record LoginResult(String accessToken, long expiresInSeconds, CurrentUser user) {
    }
}
