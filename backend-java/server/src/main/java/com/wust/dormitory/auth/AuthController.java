package com.wust.dormitory.auth;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.AuthApi;
import com.wust.dormitory.model.dto.ActivateRequest;
import com.wust.dormitory.model.dto.ChangePasswordRequest;
import com.wust.dormitory.model.dto.CurrentUserData;
import com.wust.dormitory.model.dto.CurrentUserSuccessResponse;
import com.wust.dormitory.model.dto.LoginData;
import com.wust.dormitory.model.dto.LoginRequest;
import com.wust.dormitory.model.dto.LoginSuccessResponse;
import com.wust.dormitory.model.dto.VoidSuccessResponse;
import com.wust.dormitory.security.AuthTokenService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.FeatureAccessService;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;

@RestController
public class AuthController implements AuthApi {
    private final AuthService authService;
    private final AuthTokenService tokenService;
    private final StudentWelcomeService welcomeService;
    private final FeatureAccessService featureAccessService;

    public AuthController(
            AuthService authService,
            AuthTokenService tokenService,
            StudentWelcomeService welcomeService,
            FeatureAccessService featureAccessService) {
        this.authService = authService;
        this.tokenService = tokenService;
        this.welcomeService = welcomeService;
        this.featureAccessService = featureAccessService;
    }

    @Override
    public ResponseEntity<LoginSuccessResponse> login(LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.getUsername(), request.getPassword());
        LoginData data = new LoginData();
        data.setAccessToken(result.accessToken());
        data.setTokenType("Bearer");
        data.setExpiresInSeconds(result.expiresInSeconds());
        data.setUser(toData(result.user()));

        LoginSuccessResponse response = new LoginSuccessResponse();
        response.setSuccess(true);
        response.setRequestId(requestId());
        response.setTimestamp(new Date());
        response.setData(data);
        response.setError(null);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> activate(ActivateRequest request) {
        authService.activate(request.getStudentNumber(), request.getStudentName(), request.getPassword());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> logout() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getCredentials() instanceof String token) {
            tokenService.revoke(token);
        }
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> changePassword(ChangePasswordRequest request) {
        authService.changePassword(SecurityUsers.current(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<CurrentUserSuccessResponse> getCurrentUser() {
        CurrentUserSuccessResponse response = new CurrentUserSuccessResponse();
        response.setSuccess(true);
        response.setRequestId(requestId());
        response.setTimestamp(new Date());
        response.setData(toData(SecurityUsers.current()));
        response.setError(null);
        return ResponseEntity.ok(response);
    }

    private CurrentUserData toData(CurrentUser user) {
        CurrentUserData data = new CurrentUserData();
        data.setUserId(user.userId());
        data.setStudentId(user.studentId());
        data.setUsername(user.username());
        data.setDisplayName(user.displayName());
        data.setUserType(user.userType());
        data.setFeatures(featureAccessService.currentFeatures().stream().sorted().toList());
        data.setWelcome(welcomeService.welcomeFor(user));
        return data;
    }

    private String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null ? "unknown" : requestId;
    }
}
