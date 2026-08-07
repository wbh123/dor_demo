package com.wust.dormitory.student;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/teams/{teamId}/selection-members")
public class TeamSelectionMemberController {
    private final TeamSelectionMemberService service;

    public TeamSelectionMemberController(TeamSelectionMemberService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ListSuccessResponse> list(@PathVariable long teamId) {
        CurrentUser user = SecurityUsers.requireStudent();
        Long studentId = user.studentId();
        if (studentId == null) {
            throw new BusinessException(
                    "STUDENT_CONTEXT_REQUIRED",
                    "当前登录账号未绑定学生信息",
                    HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(ResponseFactory.list(
                service.confirmedMembers(teamId, studentId.longValue())));
    }
}
