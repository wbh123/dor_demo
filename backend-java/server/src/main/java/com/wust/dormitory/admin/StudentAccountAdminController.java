package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.StudentAccountAdminApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.StudentPasswordResetRequest;
import com.wust.dormitory.model.dto.StudentStateResetRequest;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentAccountAdminController implements StudentAccountAdminApi {
    private final StudentAccountAdminService service;

    public StudentAccountAdminController(StudentAccountAdminService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> resetStudentPassword(
            Long studentId,
            StudentPasswordResetRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(
                service.resetPassword(
                        studentId,
                        request.getReason(),
                        SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> resetStudentState(
            Long studentId,
            StudentStateResetRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(
                service.resetState(
                        studentId,
                        request.getConfirmStudentNumber(),
                        request.getReason(),
                        SecurityUsers.requireAdmin())));
    }
}
