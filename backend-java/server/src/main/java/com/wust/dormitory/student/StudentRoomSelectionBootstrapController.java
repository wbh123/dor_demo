package com.wust.dormitory.student;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.StudentRoomSelectionBootstrapApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentRoomSelectionBootstrapController implements StudentRoomSelectionBootstrapApi {
    private final StudentRoomSelectionBootstrapService bootstrapService;

    public StudentRoomSelectionBootstrapController(
            StudentRoomSelectionBootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getStudentRoomSelectionBootstrap(
            Long batchId,
            Long roomId,
            Long teamId) {
        CurrentUser user = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(
                bootstrapService.bootstrap(batchId, roomId, teamId, user)));
    }
}
