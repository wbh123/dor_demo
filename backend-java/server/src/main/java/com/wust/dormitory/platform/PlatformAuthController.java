package com.wust.dormitory.platform;

import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformAuthController {
    private final PlatformAuthService authService;

    public PlatformAuthController(PlatformAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<PlatformAuthService.LoginResult> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.username(), request.password()));
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUser> me() {
        return ResponseEntity.ok(SecurityUsers.requireSystemAdmin());
    }

    @PostMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody PasswordChangeRequest request) {
        authService.changePassword(request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(Map.of(
                "changed", true,
                "reloginRequired", true,
                "message", "密码修改成功，请使用新密码重新登录"
        ));
    }

    public record LoginRequest(String username, String password) {
    }

    public record PasswordChangeRequest(String currentPassword, String newPassword) {
    }
}
