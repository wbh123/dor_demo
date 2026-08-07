package com.wust.dormitory.student.model.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

public record StudentSelectionActiveTeamRow(
        long id,
        long batchId,
        String teamStatus,
        long leaderStudentId,
        String memberRole,
        String memberStatus) {

    public Map<String, Object> asResponseMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("batch_id", batchId);
        result.put("team_status", teamStatus);
        result.put("leader_student_id", leaderStudentId);
        result.put("member_role", memberRole);
        result.put("member_status", memberStatus);
        return result;
    }
}
