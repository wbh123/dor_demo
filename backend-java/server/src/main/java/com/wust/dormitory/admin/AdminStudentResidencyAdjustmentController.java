package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/students/{studentId}")
public class AdminStudentResidencyAdjustmentController {
    private final AdminStudentResidencyAdjustmentService service;

    public AdminStudentResidencyAdjustmentController(
            AdminStudentResidencyAdjustmentService service) {
        this.service = service;
    }

    @GetMapping("/residency-adjustment-context")
    public ResponseEntity<ObjectSuccessResponse> context(@PathVariable long studentId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.context(studentId)));
    }

    @PostMapping("/residency-adjustment")
    public ResponseEntity<ObjectSuccessResponse> adjust(
            @PathVariable long studentId,
            @RequestBody AdjustmentRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(service.adjust(
                studentId,
                request.bedId(),
                request.reason(),
                SecurityUsers.requireAdmin())));
    }

    public record AdjustmentRequest(long bedId, String reason) {
    }
}
