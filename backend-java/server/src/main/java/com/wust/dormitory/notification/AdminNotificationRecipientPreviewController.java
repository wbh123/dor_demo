package com.wust.dormitory.notification;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/governance/notifications/recipients")
public class AdminNotificationRecipientPreviewController {
    private final NotificationRecipientResolver recipientResolver;

    public AdminNotificationRecipientPreviewController(
            NotificationRecipientResolver recipientResolver) {
        this.recipientResolver = recipientResolver;
    }

    @PostMapping("/count")
    public ResponseEntity<ObjectSuccessResponse> count(
            @RequestBody RecipientPreviewRequest request) {
        SecurityUsers.requireAdmin();
        int count = recipientResolver.resolve(request.criteria()).size();
        return ResponseEntity.ok(ResponseFactory.object(Map.of("recipientCount", count)));
    }

    public record RecipientPreviewRequest(
            NotificationRecipientResolver.RecipientCriteria criteria) {
    }
}
