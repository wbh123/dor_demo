package com.wust.dormitory.notification;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/governance/notifications/options")
public class AdminNotificationOptionController {
    private final NotificationOptionService service;

    public AdminNotificationOptionController(NotificationOptionService service) {
        this.service = service;
    }

    @GetMapping("/students")
    public ResponseEntity<ListSuccessResponse> students(
            @RequestParam(required = false) String keyword) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.students(keyword)));
    }

    @GetMapping("/batches")
    public ResponseEntity<ListSuccessResponse> batches() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.batches()));
    }

    @GetMapping("/majors")
    public ResponseEntity<ListSuccessResponse> majors() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.majors()));
    }

    @GetMapping("/buildings")
    public ResponseEntity<ListSuccessResponse> buildings() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.buildings()));
    }
}
