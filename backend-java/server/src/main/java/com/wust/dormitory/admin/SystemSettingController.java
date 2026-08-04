package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.SystemSettingApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.StudentWelcomeSettingUpdateRequest;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
public class SystemSettingController implements SystemSettingApi {
    private final SystemSettingService settingService;
    private final FeatureAccessService featureAccessService;

    public SystemSettingController(
            SystemSettingService settingService,
            FeatureAccessService featureAccessService) {
        this.settingService = settingService;
        this.featureAccessService = featureAccessService;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getStudentWelcomeSetting() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(
                settingService.studentWelcome()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateStudentWelcomeSetting(
            StudentWelcomeSettingUpdateRequest request) {
        var operator = SecurityUsers.requireAdmin();
        requireMultilingualPermissionForForeignMessages(request);
        return ResponseEntity.ok(ResponseFactory.object(
                settingService.updateStudentWelcome(
                        request.getMessages(),
                        request.getCountryMessages(),
                        request.getExpectedVersion(),
                        operator)));
    }

    @SuppressWarnings("unchecked")
    private void requireMultilingualPermissionForForeignMessages(
            StudentWelcomeSettingUpdateRequest request) {
        if (featureAccessService.has(FeatureCodes.P2_MULTILINGUAL_INTERFACE)) {
            return;
        }
        Map<String, Object> current = settingService.studentWelcome();
        Map<String, String> currentMessages = (Map<String, String>) current.get("messages");
        Map<String, String> currentCountries = (Map<String, String>) current.get("countryMessages");
        String currentEnglish = currentMessages == null ? null : currentMessages.get("en-US");
        String requestedEnglish = request.getMessages() == null ? null : request.getMessages().get("en-US");
        if (!Objects.equals(currentEnglish, requestedEnglish)
                || !Objects.equals(currentCountries, request.getCountryMessages())) {
            throw new BusinessException(
                    "MULTILINGUAL_WELCOME_NOT_ENABLED",
                    "当前系统权限仅允许修改中文欢迎语",
                    HttpStatus.FORBIDDEN);
        }
    }
}
