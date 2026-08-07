package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.notification.NotificationDispatchService;
import com.wust.dormitory.notification.NotificationRecipientResolver;
import com.wust.dormitory.notification.NotificationTemplateService;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/governance")
public class AdminGovernanceNotificationController {
    private final NotificationTemplateService notificationTemplateService;
    private final NotificationDispatchService notificationDispatchService;

    public AdminGovernanceNotificationController(
            NotificationTemplateService notificationTemplateService,
            NotificationDispatchService notificationDispatchService) {
        this.notificationTemplateService = notificationTemplateService;
        this.notificationDispatchService = notificationDispatchService;
    }

    @GetMapping("/notifications/templates")
    public ResponseEntity<ListSuccessResponse> templates() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(notificationTemplateService.list()));
    }

    @PostMapping("/notifications/templates/revisions")
    public ResponseEntity<ObjectSuccessResponse> createTemplate(
            @RequestBody NotificationTemplateRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(notificationTemplateService.createRevision(
                request.templateId(),
                new NotificationTemplateService.TemplateCommand(
                        request.templateCode(),
                        request.templateName(),
                        request.titleZhCn(),
                        request.contentZhCn(),
                        request.titleEnUs(),
                        request.contentEnUs(),
                        request.enabled(),
                        request.creationReason()),
                SecurityUsers.requireAdmin())));
    }

    @PostMapping("/notifications/preflight")
    public ResponseEntity<ObjectSuccessResponse> notificationPreflight(
            @RequestBody NotificationSendRequest request) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(notificationDispatchService.preflight(
                request.criteria(), request.templateRevisionId(), request.variables())));
    }

    @PostMapping("/notifications/schedule")
    public ResponseEntity<ObjectSuccessResponse> scheduleNotification(
            @RequestBody NotificationSendRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(notificationDispatchService.schedule(
                request.criteria(),
                request.templateRevisionId(),
                request.variables(),
                request.scheduledAt(),
                request.zoneId(),
                request.reason(),
                SecurityUsers.requireAdmin())));
    }

    @GetMapping("/notifications/status")
    public ResponseEntity<ListSuccessResponse> notificationStatus(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(notificationDispatchService.status(page, size)));
    }

    @PostMapping("/notifications/{taskId}/cancel")
    public ResponseEntity<ObjectSuccessResponse> cancelNotification(@PathVariable long taskId) {
        notificationDispatchService.cancel(taskId, SecurityUsers.requireAdmin());
        return ResponseEntity.ok(ResponseFactory.object(Map.of("id", taskId, "status", "CANCELLED")));
    }

    public record NotificationTemplateRequest(
            Long templateId,
            String templateCode,
            String templateName,
            String titleZhCn,
            String contentZhCn,
            String titleEnUs,
            String contentEnUs,
            boolean enabled,
            String creationReason) {
    }

    public record NotificationSendRequest(
            NotificationRecipientResolver.RecipientCriteria criteria,
            long templateRevisionId,
            Map<String, Object> variables,
            LocalDateTime scheduledAt,
            String zoneId,
            String reason) {
    }
}
