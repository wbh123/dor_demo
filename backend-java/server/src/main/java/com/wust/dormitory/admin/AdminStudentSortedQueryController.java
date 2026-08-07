package com.wust.dormitory.admin;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.AdminStudentCatalogApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminStudentSortedQueryController implements AdminStudentCatalogApi {
    private final StudentAdminSortedQueryService service;

    public AdminStudentSortedQueryController(StudentAdminSortedQueryService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getAdminStudentCatalog(
            String keyword,
            String gender,
            Long majorId,
            String studentCategory,
            String enrollmentSource,
            Integer page,
            Integer size,
            String sortField,
            String sortDirection) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.query(
                keyword,
                gender,
                majorId,
                studentCategory,
                enrollmentSource,
                page == null ? 1 : page,
                size == null ? 20 : size,
                sortField == null ? "studentNumber" : sortField,
                sortDirection == null ? "asc" : sortDirection)));
    }
}
