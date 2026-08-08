package com.wust.dormitory.platform;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.AuthTokenService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.PlatformAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class PlatformAuthService {
    private final NamedParameterJdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService tokenService;
    private final PlatformAuditService auditService;

    public PlatformAuthService(NamedParameterJdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                               AuthTokenService tokenService, PlatformAuditService auditService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditService = auditService;
    }

    public LoginResult login(String username, String password) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, student_id, username, password_hash, user_type,
                       account_status, display_name, password_change_required
                FROM app_user WHERE username=:username
                """, Map.of("username", username));
        if (rows.size() != 1) {
            throw invalidLogin();
        }
        Map<String, Object> row = rows.getFirst();
        String hash = (String) row.get("password_hash");
        if (!"SYSTEM_ADMIN".equals(String.valueOf(row.get("user_type")))
                || !"ACTIVE".equals(String.valueOf(row.get("account_status")))
                || hash == null || !passwordEncoder.matches(password, hash)) {
            throw invalidLogin();
        }
        CurrentUser user = new CurrentUser(
                ((Number) row.get("id")).longValue(), null,
                String.valueOf(row.get("username")), String.valueOf(row.get("display_name")),
                "SYSTEM_ADMIN", ((Number) row.get("password_change_required")).intValue() == 1
        );
        AuthTokenService.Token token = tokenService.issue(user);
        jdbc.update("UPDATE app_user SET last_login_at=CURRENT_TIMESTAMP(3) WHERE id=:id",
                Map.of("id", user.userId()));
        return new LoginResult(token.accessToken(), token.expiresInSeconds(), user);
    }

    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        CurrentUser user = SecurityUsers.requireSystemAdmin();
        validateNewPassword(newPassword);
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=:id",
                Map.of("id", user.userId()), String.class);
        if (hash == null || currentPassword == null || !passwordEncoder.matches(currentPassword, hash)) {
            throw new BusinessException("CURRENT_PASSWORD_INVALID", "当前密码不正确", HttpStatus.BAD_REQUEST);
        }
        jdbc.update("""
                UPDATE app_user
                SET password_hash=:hash, password_change_required=0,
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id AND user_type='SYSTEM_ADMIN'
                """, Map.of("hash", passwordEncoder.encode(newPassword), "id", user.userId()));
        auditService.success("SYSTEM_ADMIN_PASSWORD_CHANGE", user.userId(),
                "APP_USER", String.valueOf(user.userId()), "系统管理员修改本人密码", null,
                Map.of("passwordChangeRequired", false));
    }

    private void validateNewPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new BusinessException("PASSWORD_REQUIRED", "请输入新密码");
        }
    }

    private BusinessException invalidLogin() {
        return new BusinessException("AUTH_INVALID", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
    }

    public record LoginResult(String accessToken, long expiresInSeconds, CurrentUser user) {
    }
}
