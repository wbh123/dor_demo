package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.AdminResidencyAdjustmentApi;
import com.wust.dormitory.model.dto.AdminResidencyAdjustmentRequest;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminStudentResidencyAdjustmentController
        implements AdminResidencyAdjustmentApi {
    private final AdminStudentResidencyAdjustmentService service;

    public AdminStudentResidencyAdjustmentController(
            AdminStudentResidencyAdjustmentService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getStudentResidencyAdjustmentContext(
            Long studentId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.context(studentId)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> adjustStudentResidency(
            AdminResidencyAdjustmentRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(service.adjust(
                request.getStudentId(),
                request.getBedId(),
                request.getReason(),
                SecurityUsers.requireAdmin())));
    }
}
