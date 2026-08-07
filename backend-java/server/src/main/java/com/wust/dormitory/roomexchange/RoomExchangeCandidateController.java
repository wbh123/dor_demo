package com.wust.dormitory.roomexchange;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ListSuccessResponse;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/student/room-exchanges")
public class RoomExchangeCandidateController {
    private final RoomExchangeCandidateService service;

    public RoomExchangeCandidateController(RoomExchangeCandidateService service) {
        this.service = service;
    }

    @GetMapping("/exact-candidate")
    public ResponseEntity<ObjectSuccessResponse> exactCandidate(
            @RequestParam String studentNumber,
            @RequestParam String studentName,
            @RequestParam Long buildingId,
            @RequestParam String roomNumber) {
        long studentId = requiredStudentId();
        return ResponseEntity.ok(ResponseFactory.object(service.exactCandidate(
                studentId, studentNumber, studentName, buildingId, roomNumber)));
    }

    @GetMapping("/candidate-buildings")
    public ResponseEntity<ListSuccessResponse> candidateBuildings() {
        return ResponseEntity.ok(ResponseFactory.list(
                service.buildings(requiredStudentId())));
    }

    private long requiredStudentId() {
        CurrentUser user = SecurityUsers.requireStudent();
        Long studentId = user.studentId();
        if (studentId == null) {
            throw new BusinessException(
                    "STUDENT_CONTEXT_REQUIRED",
                    "当前登录账号未绑定学生信息",
                    HttpStatus.FORBIDDEN);
        }
        return studentId.longValue();
    }
}
