package com.wust.dormitory.notification;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/governance/notifications")
public class AdminDirectNotificationController {
    private final DirectNotificationService directNotificationService;

    public AdminDirectNotificationController(
            DirectNotificationService directNotificationService) {
        this.directNotificationService = directNotificationService;
    }

    @PostMapping("/direct")
    public ResponseEntity<ObjectSuccessResponse> sendDirect(
            @RequestBody DirectNotificationRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(
                directNotificationService.send(
                        request.criteria(),
                        request.titleZhCn(),
                        request.contentZhCn(),
                        request.titleEnUs(),
                        request.contentEnUs(),
                        request.reason(),
                        SecurityUsers.requireAdmin())));
    }

    public record DirectNotificationRequest(
            NotificationRecipientResolver.RecipientCriteria criteria,
            String titleZhCn,
            String contentZhCn,
            String titleEnUs,
            String contentEnUs,
            String reason) {
    }
}
