package com.wust.dormitory.student;

import com.wust.dormitory.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentRoomSelectionBootstrapService {
    private final StudentService studentService;
    private final TeamSelectionMemberService teamSelectionMemberService;

    public StudentRoomSelectionBootstrapService(
            StudentService studentService,
            TeamSelectionMemberService teamSelectionMemberService) {
        this.studentService = studentService;
        this.teamSelectionMemberService = teamSelectionMemberService;
    }

    public Map<String, Object> bootstrap(
            long batchId,
            long roomId,
            Long teamId,
            CurrentUser user) {
        Map<String, Object> roomData = studentService.room(batchId, roomId, user);
        List<Map<String, Object>> teamMembers = teamId == null
                ? List.of()
                : teamSelectionMemberService.confirmedMembers(teamId, user.studentId());
        Map<String, Object> response = new LinkedHashMap<>(roomData);
        response.put("teamMembers", teamMembers);
        response.put("serverTime", Instant.now().toString());
        return response;
    }
}
