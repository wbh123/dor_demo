package com.wust.dormitory.student;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class TeamSelectionMemberService {
    private final TeamSelectionMemberMapper mapper;

    public TeamSelectionMemberService(TeamSelectionMemberMapper mapper) {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> confirmedMembers(
            long teamId,
            long leaderStudentId) {
        if (mapper.countLeaderAccess(teamId, leaderStudentId) != 1) {
            throw new BusinessException(
                    "TEAM_LEADER_REQUIRED",
                    "只有队长可以为队伍成员选择床位",
                    HttpStatus.FORBIDDEN);
        }
        return mapper.findConfirmedMembers(teamId);
    }
}
