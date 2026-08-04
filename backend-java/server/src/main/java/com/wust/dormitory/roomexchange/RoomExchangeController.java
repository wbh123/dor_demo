package com.wust.dormitory.roomexchange;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.RoomExchangeApi;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.RoomExchangeRespondRequest;
import com.wust.dormitory.model.dto.RoomExchangeReviewRequest;
import com.wust.dormitory.model.dto.RoomExchangeSettingsRequest;
import com.wust.dormitory.model.dto.RoomExchangeSubmitRequest;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RoomExchangeController implements RoomExchangeApi {
    private final RoomExchangeService service;

    public RoomExchangeController(RoomExchangeService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listRoomExchangeCandidates() {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.list(
                service.candidates(student.studentId())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listMyRoomExchanges() {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.list(
                service.listMy(student.studentId())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> submitRoomExchange(
            RoomExchangeSubmitRequest request) {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.submit(
                student.studentId(),
                request.getTargetStudentId(),
                request.getReason(),
                student)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> respondRoomExchange(
            Long exchangeId,
            RoomExchangeRespondRequest request) {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.respond(
                exchangeId,
                student.studentId(),
                request.getAccepted(),
                request.getReason(),
                student)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> cancelRoomExchange(
            Long exchangeId,
            RoomExchangeReviewRequest request) {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.cancel(
                exchangeId,
                student.studentId(),
                request.getReason(),
                student)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getRoomExchangeSettings() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.policy()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateRoomExchangeSettings(
            RoomExchangeSettingsRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.updateSettings(
                request.getMode().getValue(),
                request.getReason(),
                admin)));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listRoomExchanges(
            String status,
            String keyword) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(
                service.listAdmin(status, keyword)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> approveRoomExchange(
            Long exchangeId,
            RoomExchangeReviewRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.approve(
                exchangeId,
                request.getReason(),
                admin)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> rejectRoomExchange(
            Long exchangeId,
            RoomExchangeReviewRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.reject(
                exchangeId,
                request.getReason(),
                admin)));
    }
}
