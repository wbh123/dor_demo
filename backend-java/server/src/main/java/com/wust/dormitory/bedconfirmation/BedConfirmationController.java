package com.wust.dormitory.bedconfirmation;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class BedConfirmationController {
    private final BedConfirmationService service;

    public BedConfirmationController(BedConfirmationService service) {
        this.service = service;
    }

    @GetMapping("/student/bed-confirmations")
    public ResponseEntity<ObjectSuccessResponse> getMyBedConfirmation() {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.my(student.studentId())));
    }

    @PostMapping("/student/bed-confirmations")
    public ResponseEntity<ObjectSuccessResponse> submitBedConfirmation(
            @RequestBody SubmitRequest request) {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.submit(
                student.studentId(), request.bedId(), request.reason(), student)));
    }

    @PostMapping("/student/bed-confirmations/{requestId}/cancel")
    public ResponseEntity<ObjectSuccessResponse> cancelBedConfirmation(
            @PathVariable long requestId,
            @RequestBody ActionRequest request) {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.cancel(
                requestId, student.studentId(), request.reason(), student)));
    }

    @GetMapping("/admin/bed-confirmations/rooms")
    public ResponseEntity<ListSuccessResponse> listBedConfirmationRooms(
            @RequestParam(required = false) String keyword) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(service.rooms(keyword)));
    }

    @GetMapping("/admin/bed-confirmations/rooms/{roomId}")
    public ResponseEntity<ObjectSuccessResponse> getBedConfirmationRoom(
            @PathVariable long roomId) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.room(roomId)));
    }

    @PostMapping("/admin/bed-confirmations/rooms/{roomId}/approve")
    public ResponseEntity<ObjectSuccessResponse> approveRoomBedConfirmations(
            @PathVariable long roomId,
            @RequestBody ActionRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.approveRoom(
                roomId, request.reason(), admin)));
    }

    @PostMapping("/admin/bed-confirmations/requests/{requestId}/reject")
    public ResponseEntity<ObjectSuccessResponse> rejectBedConfirmation(
            @PathVariable long requestId,
            @RequestBody ActionRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.reject(
                requestId, request.reason(), admin)));
    }

    public record SubmitRequest(long bedId, String reason) { }
    public record ActionRequest(String reason) { }
}
