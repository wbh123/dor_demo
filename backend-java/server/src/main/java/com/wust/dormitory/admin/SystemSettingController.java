package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.SystemSettingApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.StudentWelcomeSettingUpdateRequest;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemSettingController implements SystemSettingApi {
    private final SystemSettingService settingService;

    public SystemSettingController(SystemSettingService settingService) {
        this.settingService = settingService;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getStudentWelcomeSetting() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(settingService.studentWelcome()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateStudentWelcomeSetting(
            StudentWelcomeSettingUpdateRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(
                settingService.updateStudentWelcome(
                        request.getMessages(),
                        request.getCountryMessages(),
                        request.getExpectedVersion(),
                        SecurityUsers.requireAdmin())));
    }
}
