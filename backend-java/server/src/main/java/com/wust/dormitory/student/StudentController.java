package com.wust.dormitory.student;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.StudentApi;
import com.wust.dormitory.model.dto.HoldTokenRequest;
import com.wust.dormitory.model.dto.InvitationResponseRequest;
import com.wust.dormitory.model.dto.InviteRequest;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.TeamBedsRequest;
import com.wust.dormitory.model.dto.TeamConfirmRequest;
import com.wust.dormitory.model.dto.VoidSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.selection.BedHoldService;
import com.wust.dormitory.selection.BedScopeGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class StudentController implements StudentApi {
    private final StudentService studentService;
    private final TeamService teamService;
    private final TeamHoldReleaseService teamHoldReleaseService;
    private final BedScopeGuard bedScopeGuard;

    public StudentController(StudentService studentService,
                             TeamService teamService,
                             TeamHoldReleaseService teamHoldReleaseService,
                             BedScopeGuard bedScopeGuard) {
        this.studentService = studentService;
        this.teamService = teamService;
        this.teamHoldReleaseService = teamHoldReleaseService;
        this.bedScopeGuard = bedScopeGuard;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getStudentProfile() {
        return ResponseEntity.ok(ResponseFactory.object(studentService.profile(student())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listStudentBatches() {
        return ResponseEntity.ok(ResponseFactory.list(studentService.batches(student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getQuestionnaire(Long batchId) {
        return ResponseEntity.ok(ResponseFactory.object(studentService.questionnaire(batchId, student())));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> submitQuestionnaire(
            Long batchId, Map<String, Object> requestBody) {
        studentService.submitQuestionnaire(batchId, requestBody, student());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listCandidateRooms(Long batchId) {
        return ResponseEntity.ok(ResponseFactory.list(studentService.rooms(batchId, student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getCandidateRoom(Long batchId, Long roomId) {
        Map<String, Object> snapshot = studentService.room(batchId, roomId, student());
        return ResponseEntity.ok(ResponseFactory.object(
                bedScopeGuard.filterRoomSnapshot(batchId, snapshot)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> holdBed(Long batchId, Long bedId) {
        bedScopeGuard.requireAllowed(batchId, bedId);
        BedHoldService.HoldResult hold = studentService.hold(batchId, bedId, student());
        return ResponseEntity.ok(ResponseFactory.object(Map.of(
                "token", hold.token(),
                "expiresAt", hold.expiresAt())));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> releaseBed(
            Long batchId, Long bedId, HoldTokenRequest request) {
        bedScopeGuard.requireAllowed(batchId, bedId);
        studentService.release(batchId, bedId, request.getToken(), student());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> confirmBed(
            Long batchId, Long bedId, HoldTokenRequest request) {
        bedScopeGuard.requireAllowed(batchId, bedId);
        return ResponseEntity.ok(ResponseFactory.object(
                studentService.confirm(batchId, bedId, request.getToken(), student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getRandomRecommendation(Long batchId) {
        return ResponseEntity.ok(ResponseFactory.object(
                studentService.randomRecommendation(batchId, student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getMyAssignment(Long batchId) {
        return ResponseEntity.ok(ResponseFactory.object(studentService.assignment(batchId, student())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listMyTeams() {
        return ResponseEntity.ok(ResponseFactory.list(teamService.teams(student())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listTeamInvitations() {
        return ResponseEntity.ok(ResponseFactory.list(teamService.invitations(student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> inviteTeammate(InviteRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(
                teamService.inviteTeammate(request.getStudentNumber(), student())));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> respondTeamInvitation(InvitationResponseRequest request) {
        studentService.respondInvitation(
                request.getInvitationToken(), request.getAccepted(), student());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> lockTeam(Long teamId) {
        studentService.lockTeam(teamId, student());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> holdTeamBeds(
            Long batchId, Long teamId, TeamBedsRequest request) {
        List<Long> bedIds = new ArrayList<>(request.getBedIds());
        bedScopeGuard.requireAllowed(batchId, bedIds);
        BedHoldService.HoldResult hold = studentService.holdTeam(
                batchId, teamId, bedIds, student());
        return ResponseEntity.ok(ResponseFactory.object(Map.of(
                "token", hold.token(),
                "expiresAt", hold.expiresAt())));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> releaseTeamBeds(
            Long batchId, Long teamId, TeamConfirmRequest request) {
        CurrentUser user = student();
        List<Long> bedIds = new ArrayList<>(request.getBedIds());
        bedScopeGuard.requireAllowed(batchId, bedIds);
        teamHoldReleaseService.release(
                batchId, teamId, bedIds, request.getToken(), user.studentId());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> confirmTeamBeds(
            Long batchId, Long teamId, TeamConfirmRequest request) {
        List<Long> bedIds = new ArrayList<>(request.getBedIds());
        bedScopeGuard.requireAllowed(batchId, bedIds);
        studentService.confirmTeam(
                batchId, teamId, bedIds, request.getToken(), student());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    private CurrentUser student() {
        return SecurityUsers.requireStudent();
    }
}
