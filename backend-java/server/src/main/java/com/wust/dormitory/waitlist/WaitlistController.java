package com.wust.dormitory.waitlist;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.WaitlistApi;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.WaitlistActionRequest;
import com.wust.dormitory.model.dto.WaitlistJoinRequest;
import com.wust.dormitory.model.dto.WaitlistPolicyRequest;
import com.wust.dormitory.model.dto.WaitlistPriorityRequest;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WaitlistController implements WaitlistApi {
    private final WaitlistService service;

    public WaitlistController(WaitlistService service) {
        this.service = service;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getStudentWaitlistPolicy() {
        SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.policy()));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listStudentWaitlistCandidates() {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.list(
                service.candidates(student.studentId())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listMyWaitlistEntries() {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.list(
                service.listMy(student.studentId())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> joinWaitlist(
            WaitlistJoinRequest request) {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.join(
                student.studentId(),
                request.getTargetRoomId(),
                request.getTargetBedId(),
                request.getReason(),
                student)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> withdrawWaitlistEntry(
            Long entryId,
            WaitlistActionRequest request) {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.withdraw(
                entryId,
                student.studentId(),
                request.getReason(),
                student)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> acceptWaitlistOffer(
            Long offerId,
            WaitlistActionRequest request) {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.accept(
                offerId,
                student.studentId(),
                request.getReason(),
                student)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> rejectWaitlistOffer(
            Long offerId,
            WaitlistActionRequest request) {
        CurrentUser student = SecurityUsers.requireStudent();
        return ResponseEntity.ok(ResponseFactory.object(service.reject(
                offerId,
                student.studentId(),
                request.getReason(),
                student)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getWaitlistSettings() {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.policy()));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateWaitlistSettings(
            WaitlistPolicyRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.updateSettings(
                request.getEnabled(),
                request.getOfferTtlMinutes(),
                request.getPriorityMode().getValue(),
                request.getScanBatchSize(),
                request.getReason(),
                admin)));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listAdminWaitlistEntries(
            String status,
            String keyword) {
        SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.list(
                service.listAdmin(status, keyword)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateWaitlistPriority(
            Long entryId,
            WaitlistPriorityRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.updatePriority(
                entryId,
                request.getPriorityScore(),
                request.getReason(),
                admin)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createWaitlistOffer(
            Long entryId,
            WaitlistActionRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.createOffer(
                entryId,
                request.getReason(),
                admin)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> directlyAssignWaitlistEntry(
            Long entryId,
            WaitlistActionRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(service.directAssign(
                entryId,
                request.getReason(),
                admin)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> scanWaitlist(
            WaitlistActionRequest request) {
        CurrentUser admin = SecurityUsers.requireAdmin();
        return ResponseEntity.ok(ResponseFactory.object(
                service.scanAvailableResources(admin)));
    }
}
