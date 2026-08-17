package com.wust.dormitory.readiness;

import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/system-readiness")
public class SystemReadinessController {
    private final SystemReadinessService service;

    public SystemReadinessController(SystemReadinessService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SystemReadinessReport> getSystemReadiness() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(service.check());
    }
}
