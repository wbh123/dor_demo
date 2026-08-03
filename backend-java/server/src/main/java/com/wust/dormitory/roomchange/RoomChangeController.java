package com.wust.dormitory.roomchange;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.RoomChangeApi;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.RoomChangeReviewRequest;
import com.wust.dormitory.model.dto.RoomChangeSettingsRequest;
import com.wust.dormitory.model.dto.RoomChangeSubmitRequest;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomChangeController implements RoomChangeApi {
    private final RoomChangeService service;

    public RoomChangeController(RoomChangeService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getRoomChangePolicy() {
        SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.policy()));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listRoomChangeCandidates() {
        CurrentUser user = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.list(service.candidates(user.studentId())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listMyRoomChangeRequests() {
        CurrentUser user = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.list(service.listMy(user.studentId())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> submitRoomChangeRequest(
            RoomChangeSubmitRequest request) {
        CurrentUser user = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.submit(
                user.studentId(),
                request.getTargetRoomId(),
                request.getTargetBedId(),
                request.getReason(),
                user)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> cancelMyRoomChangeRequest(
            Long requestId,
            RoomChangeReviewRequest request) {
        CurrentUser user = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.cancel(
                requestId,
                user.studentId(),
                request.getReason(),
                user)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getRoomChangeSettings() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.policy()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateRoomChangeSettings(
            RoomChangeSettingsRequest request) {
        CurrentUser user = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.updateSettings(
                request.getMode().getValue(),
                request.getReason(),
                user)));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listRoomChangeRequests(
            String status,
            String keyword) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.listAll(status, keyword)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> approveRoomChangeRequest(
            Long requestId,
            RoomChangeReviewRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(service.approve(
                requestId,
                request.getReason(),
                SecurityUsers.requireAdmin())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> rejectRoomChangeRequest(
            Long requestId,
            RoomChangeReviewRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(service.reject(
                requestId,
                request.getReason(),
                SecurityUsers.requireAdmin())));
    }
}
