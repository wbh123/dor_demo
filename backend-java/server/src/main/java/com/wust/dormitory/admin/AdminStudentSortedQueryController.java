package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/student-catalog")
public class AdminStudentSortedQueryController {
    private final StudentAdminSortedQueryService service;

    public AdminStudentSortedQueryController(StudentAdminSortedQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ObjectSuccessResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) Long majorId,
            @RequestParam(required = false) String studentCategory,
            @RequestParam(required = false) String enrollmentSource,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "studentNumber") String sortField,
            @RequestParam(defaultValue = "asc") String sortDirection) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.query(
                keyword, gender, majorId, studentCategory, enrollmentSource,
                page == null ? 1 : page, size == null ? 20 : size, sortField, sortDirection)));
    }
}
