package com.wust.dormitory.auth;

import com.wust.dormitory.model.dto.LoginRequest;
import com.wust.dormitory.model.dto.LoginSuccessResponse;
import com.wust.dormitory.security.AuthTokenService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerFeatureProjectionTest {

    @Test
    void loginReturnsEffectiveBusinessFeatures() {
        AuthService authService = mock(AuthService.class);
        AuthTokenService tokenService = mock(AuthTokenService.class);
        StudentWelcomeService welcomeService = mock(StudentWelcomeService.class);
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        AuthController controller = new AuthController(
                authService,
                tokenService,
                welcomeService,
                featureAccessService);

        CurrentUser user = new CurrentUser(
                7L,
                null,
                "admin",
                "管理员",
                "ADMIN",
                false);
        when(authService.login("admin", "Admin123!"))
                .thenReturn(new AuthService.LoginResult("token", 3600L, user));
        when(featureAccessService.currentFeatures())
                .thenReturn(Set.of(FeatureCodes.P2_BED_SELECTION_MODE));
        when(welcomeService.welcomeFor(user)).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("Admin123!");
        ResponseEntity<LoginSuccessResponse> response = controller.login(request);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNotNull();
        assertThat(response.getBody().getData().getUser().getFeatures())
                .containsExactly(FeatureCodes.P2_BED_SELECTION_MODE);
    }
}
