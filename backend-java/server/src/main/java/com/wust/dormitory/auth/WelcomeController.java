package com.wust.dormitory.auth;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.WelcomeApi;
import com.wust.dormitory.model.dto.VoidSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WelcomeController implements WelcomeApi {
    private final StudentWelcomeService welcomeService;

    public WelcomeController(StudentWelcomeService welcomeService) {
        this.welcomeService = welcomeService;
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> acknowledgeStudentWelcome() {
        welcomeService.acknowledge(SecurityUsers.current());
        return ResponseEntity.ok(ResponseFactory.empty());
    }
}
