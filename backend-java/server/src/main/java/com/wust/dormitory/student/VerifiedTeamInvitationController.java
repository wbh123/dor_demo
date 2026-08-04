package com.wust.dormitory.student;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student")
public class VerifiedTeamInvitationController {
    private final VerifiedTeamInvitationService service;

    public VerifiedTeamInvitationController(VerifiedTeamInvitationService service) {
        this.service = service;
    }

    @PostMapping("/team-invitations/verified")
    public ResponseEntity<ObjectSuccessResponse> invite(@RequestBody VerifiedInviteRequest request) {
        return ResponseEntity.ok(ResponseFactory.object(service.invite(
                request.studentNumber(),
                request.studentName(),
                SecurityUsers.requireStudent())));
    }

    @DeleteMapping("/teams/{teamId}/invitations/{studentId}")
    public ResponseEntity<ObjectSuccessResponse> cancelTeamInvitation(
            @PathVariable long teamId,
            @PathVariable long studentId) {
        return ResponseEntity.ok(ResponseFactory.object(service.cancelInvitation(
                teamId,
                studentId,
                SecurityUsers.requireStudent())));
    }

    public record VerifiedInviteRequest(String studentNumber, String studentName) {
    }
}
