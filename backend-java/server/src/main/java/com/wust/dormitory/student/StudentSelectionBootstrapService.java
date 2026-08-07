package com.wust.dormitory.student;

import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.selection.SelectionPolicyService;
import com.wust.dormitory.student.mapper.StudentSelectionBootstrapMapper;
import com.wust.dormitory.student.model.persistence.StudentSelectionActiveTeamRow;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentSelectionBootstrapService {
    private final StudentSelectionBootstrapMapper bootstrapMapper;
    private final SelectionPolicyService selectionPolicyService;
    private final StudentRoomRecommendationService recommendationService;

    public StudentSelectionBootstrapService(
            StudentSelectionBootstrapMapper bootstrapMapper,
            SelectionPolicyService selectionPolicyService,
            StudentRoomRecommendationService recommendationService) {
        this.bootstrapMapper = bootstrapMapper;
        this.selectionPolicyService = selectionPolicyService;
        this.recommendationService = recommendationService;
    }

    public Map<String, Object> bootstrap(long batchId, Long teamId, CurrentUser user) {
        StudentSelectionActiveTeamRow activeTeam =
                bootstrapMapper.findActiveTeam(batchId, user.studentId());
        boolean explicitTeamMode = activeTeam != null
                && teamId != null
                && activeTeam.id() == teamId.longValue();
        boolean requiresPersonalTeamExit = activeTeam != null && !explicitTeamMode;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("activePersonalTeam", activeTeam == null ? null : activeTeam.asResponseMap());
        result.put("requiresPersonalTeamExit", requiresPersonalTeamExit);
        if (requiresPersonalTeamExit) {
            result.put("selectionReadiness", Map.of());
            result.put("rooms", List.of());
            return result;
        }

        result.put("selectionReadiness",
                selectionPolicyService.readiness(batchId, user.studentId()));
        result.put("rooms", recommendationService.rooms(batchId, user));
        return result;
    }
}
