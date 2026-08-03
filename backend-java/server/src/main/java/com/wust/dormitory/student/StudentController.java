package com.wust.dormitory.student;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.api.StudentApi;
import com.wust.dormitory.model.dto.HoldTokenRequest;
import com.wust.dormitory.model.dto.InvitationResponseRequest;
import com.wust.dormitory.model.dto.InviteRequest;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.StudentBedConfirmationRequest;
import com.wust.dormitory.model.dto.StudentPhoneUpdateRequest;
import com.wust.dormitory.model.dto.TeamBedsRequest;
import com.wust.dormitory.model.dto.TeamConfirmRequest;
import com.wust.dormitory.model.dto.VoidSuccessResponse;
import com.wust.dormitory.residency.BedResidencySynchronizationService;
import com.wust.dormitory.residency.ResidencyService;
import com.wust.dormitory.residency.RoomSelectionService;
import com.wust.dormitory.residency.SelectionModeGuard;
import com.wust.dormitory.residency.TeamCategoryGuard;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import com.wust.dormitory.selection.BedHoldService;
import com.wust.dormitory.selection.BedScopeGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
public class StudentController implements StudentApi {
    private final StudentService studentService;
    private final StudentProfileService profileService;
    private final StudentRoomLayoutService roomLayoutService;
    private final StudentRoomRecommendationService recommendationService;
    private final TeamService teamService;
    private final TeamHoldReleaseService teamHoldReleaseService;
    private final BedScopeGuard bedScopeGuard;
    private final RoomSelectionService roomSelectionService;
    private final ResidencyService residencyService;
    private final SelectionModeGuard selectionModeGuard;
    private final BedResidencySynchronizationService bedResidencySynchronizationService;
    private final TeamCategoryGuard teamCategoryGuard;

    public StudentController(
            StudentService studentService,
            StudentProfileService profileService,
            StudentRoomLayoutService roomLayoutService,
            StudentRoomRecommendationService recommendationService,
            TeamService teamService,
            TeamHoldReleaseService teamHoldReleaseService,
            BedScopeGuard bedScopeGuard,
            RoomSelectionService roomSelectionService,
            ResidencyService residencyService,
            SelectionModeGuard selectionModeGuard,
            BedResidencySynchronizationService bedResidencySynchronizationService,
            TeamCategoryGuard teamCategoryGuard) {
        this.studentService = studentService;
        this.profileService = profileService;
        this.roomLayoutService = roomLayoutService;
        this.recommendationService = recommendationService;
        this.teamService = teamService;
        this.teamHoldReleaseService = teamHoldReleaseService;
        this.bedScopeGuard = bedScopeGuard;
        this.roomSelectionService = roomSelectionService;
        this.residencyService = residencyService;
        this.selectionModeGuard = selectionModeGuard;
        this.bedResidencySynchronizationService = bedResidencySynchronizationService;
        this.teamCategoryGuard = teamCategoryGuard;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getStudentProfile() {
        return ResponseEntity.ok(ResponseFactory.object(profileService.profile(student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> updateStudentPhoneNumber(
            StudentPhoneUpdateRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(
                profileService.updatePhoneNumber(request.getPhoneNumber(), student())));
    }

    @Override
    public ResponseEntity<ListSuccessResponse> listNotifications() {
        return ResponseEntity.ok(ResponseFactory.list(teamService.notifications(student())));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> markNotificationRead(Long notificationId) {
        teamService.markNotificationRead(notificationId, student());
        return ResponseEntity.ok(ResponseFactory.empty());
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
        return ResponseEntity.ok(ResponseFactory.list(
                recommendationService.rooms(batchId, student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getCandidateRoom(Long batchId, Long roomId) {
        CurrentUser user = student();
        if ("ROOM".equals(selectionModeGuard.mode(batchId))) {
            return ResponseEntity.ok(ResponseFactory.object(
                    Map.of(
                            "selectionMode", "ROOM",
                            "room", recommendationService.room(batchId, roomId, user),
                            "bedsVisible", false,
                            "message", "当前批次只选择寝室，具体床位由寝室成员入住后自行协商")));
        }
        Map<String, Object> snapshot = roomLayoutService.enrich(
                studentService.room(batchId, roomId, user));
        return ResponseEntity.ok(ResponseFactory.object(
                bedScopeGuard.filterRoomSnapshot(batchId, snapshot)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> selectRoom(Long batchId, Long roomId) {
        selectionModeGuard.requireRoomMode(batchId);
        return ResponseEntity.ok(ResponseFactory.object(
                roomSelectionService.selectPersonal(batchId, roomId, student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> selectTeamRoom(
            Long batchId,
            Long teamId,
            Long roomId) {
        selectionModeGuard.requireRoomMode(batchId);
        return ResponseEntity.ok(ResponseFactory.object(
                roomSelectionService.selectTeam(batchId, teamId, roomId, student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getMyResidency() {
        CurrentUser user = student();
        return ResponseEntity.ok(ResponseFactory.object(
                residencyService.current(user.studentId())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> confirmMyActualBed(
            StudentBedConfirmationRequest request) {
        CurrentUser user = student();
        return ResponseEntity.ok(ResponseFactory.object(
                residencyService.confirmOwnBed(user.studentId(), request.getBedId(), user)));
    }

    @Override
    @Transactional
    public ResponseEntity<ObjectSuccessResponse> preparePersonalSelection(Long batchId) {
        CurrentUser user = student();
        teamHoldReleaseService.requireNoActiveHoldForPersonalSelection(
                batchId,
                user.studentId());
        return ResponseEntity.ok(ResponseFactory.object(
                teamService.preparePersonalSelection(batchId, user)));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> holdBed(Long batchId, Long bedId) {
        selectionModeGuard.requireBedMode(batchId);
        bedScopeGuard.requireAllowed(batchId, bedId);
        BedHoldService.HoldResult hold = studentService.hold(batchId, bedId, student());
        return ResponseEntity.ok(ResponseFactory.object(Map.of(
                "token", hold.token(),
                "expiresAt", hold.expiresAt())));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> releaseBed(
            Long batchId, Long bedId, HoldTokenRequest request) {
        selectionModeGuard.requireBedMode(batchId);
        bedScopeGuard.requireAllowed(batchId, bedId);
        studentService.release(batchId, bedId, request.getToken(), student());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    @Transactional
    public ResponseEntity<ObjectSuccessResponse> confirmBed(
            Long batchId, Long bedId, HoldTokenRequest request) {
        selectionModeGuard.requireBedMode(batchId);
        bedScopeGuard.requireAllowed(batchId, bedId);
        CurrentUser user = student();
        Map<String, Object> result = studentService.confirm(
                batchId, bedId, request.getToken(), user);
        bedResidencySynchronizationService.synchronizeStudent(
                batchId, user.studentId(), user);
        return ResponseEntity.ok(ResponseFactory.object(result));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getRandomRecommendation(Long batchId) {
        return ResponseEntity.ok(ResponseFactory.object(
                recommendationService.randomRecommendation(batchId, student())));
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> getMyAssignment(Long batchId) {
        CurrentUser user = student();
        if ("ROOM".equals(selectionModeGuard.mode(batchId))) {
            return ResponseEntity.ok(ResponseFactory.object(
                    residencyService.current(user.studentId())));
        }
        return ResponseEntity.ok(ResponseFactory.object(
                studentService.assignment(batchId, user)));
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
    @Transactional
    public ResponseEntity<ObjectSuccessResponse> inviteTeammate(InviteRequest request) {
        CurrentUser user = student();
        teamCategoryGuard.requireInvitationAllowed(request.getStudentNumber(), user);
        return ResponseEntity.ok(ResponseFactory.object(
                teamService.inviteTeammate(request.getStudentNumber(), user)));
    }

    @Override
    public ResponseEntity<VoidSuccessResponse> respondTeamInvitation(
            InvitationResponseRequest request) {
        teamService.respondInvitation(
                request.getInvitationToken(), request.getAccepted(), student());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    @Transactional
    public ResponseEntity<ObjectSuccessResponse> lockTeam(Long teamId) {
        CurrentUser user = student();
        teamCategoryGuard.requireLockAllowed(teamId, user);
        return ResponseEntity.ok(ResponseFactory.object(teamService.lockTeam(teamId, user)));
    }

    @Override
    @Transactional
    public ResponseEntity<ObjectSuccessResponse> leaveTeam(Long teamId) {
        CurrentUser user = student();
        teamHoldReleaseService.requireNoActiveHoldForMember(teamId, user.studentId());
        return ResponseEntity.ok(ResponseFactory.object(teamService.leaveTeam(teamId, user)));
    }

    @Override
    @Transactional
    public ResponseEntity<ObjectSuccessResponse> removeTeamMember(
            Long teamId, Long studentId) {
        CurrentUser user = student();
        teamHoldReleaseService.requireNoActiveHoldForLeader(teamId, user.studentId());
        return ResponseEntity.ok(ResponseFactory.object(
                teamService.removeMember(teamId, studentId, user)));
    }

    @Override
    @Transactional
    public ResponseEntity<ObjectSuccessResponse> holdTeamBeds(
            Long batchId, Long teamId, TeamBedsRequest request) {
        selectionModeGuard.requireBedMode(batchId);
        CurrentUser user = student();
        teamHoldReleaseService.lockTeamForHold(batchId, teamId, user.studentId());
        List<Long> bedIds = new ArrayList<>(request.getBedIds());
        bedScopeGuard.requireAllowed(batchId, bedIds);
        BedHoldService.HoldResult hold = studentService.holdTeam(
                batchId, teamId, bedIds, user);
        return ResponseEntity.ok(ResponseFactory.object(Map.of(
                "token", hold.token(),
                "expiresAt", hold.expiresAt())));
    }

    @Override
    @Transactional
    public ResponseEntity<VoidSuccessResponse> releaseTeamBeds(
            Long batchId, Long teamId, TeamConfirmRequest request) {
        selectionModeGuard.requireBedMode(batchId);
        CurrentUser user = student();
        List<Long> bedIds = new ArrayList<>(request.getBedIds());
        bedScopeGuard.requireAllowed(batchId, bedIds);
        teamHoldReleaseService.release(
                batchId, teamId, bedIds, request.getToken(), user.studentId());
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    @Override
    @Transactional
    public ResponseEntity<VoidSuccessResponse> confirmTeamBeds(
            Long batchId, Long teamId, TeamConfirmRequest request) {
        selectionModeGuard.requireBedMode(batchId);
        CurrentUser user = student();
        teamHoldReleaseService.lockTeamForHold(batchId, teamId, user.studentId());
        List<Long> bedIds = new ArrayList<>(request.getBedIds());
        bedScopeGuard.requireAllowed(batchId, bedIds);
        studentService.confirmTeam(
                batchId, teamId, bedIds, request.getToken(), user);
        bedResidencySynchronizationService.synchronizeTeam(batchId, teamId, user);
        return ResponseEntity.ok(ResponseFactory.empty());
    }

    private CurrentUser student() {
        return SecurityUsers.requireStudent();
    }
}
