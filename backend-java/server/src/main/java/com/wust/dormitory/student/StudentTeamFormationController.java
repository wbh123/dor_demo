package com.wust.dormitory.student;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/teams")
public class StudentTeamFormationController {
    private final TeamFormationService service;
    public StudentTeamFormationController(TeamFormationService service) { this.service = service; }
    @PostMapping
    public ResponseEntity<ObjectSuccessResponse> create() {
        return ResponseEntity.ok(ResponseFactory.object(service.create(SecurityUsers.requireStudent())));
    }
}
