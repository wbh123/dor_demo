package com.wust.dormitory.student;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.StudentSelectionBootstrapApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentSelectionBootstrapController implements StudentSelectionBootstrapApi {
    private final StudentSelectionBootstrapService bootstrapService;

    public StudentSelectionBootstrapController(StudentSelectionBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getStudentSelectionBootstrap(
            Long batchId,
            Long teamId) {
        CurrentUser user = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(
                bootstrapService.bootstrap(batchId, teamId, user)));
    }
}
