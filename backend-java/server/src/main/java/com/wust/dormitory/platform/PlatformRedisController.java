package com.wust.dormitory.platform;

import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform/redis")
public class PlatformRedisController {
    private final PlatformRedisService service;

    public PlatformRedisController(PlatformRedisService service) {
        this.service = service;
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clear(@RequestBody ClearRequest request) {
        CurrentUser operator = SecurityUsers.requirePlatformOperation();
        return ResponseEntity.ok(service.clear(
                request.confirmation(),
                request.reason(),
                operator));
    }

    public record ClearRequest(String confirmation, String reason) { }
}
