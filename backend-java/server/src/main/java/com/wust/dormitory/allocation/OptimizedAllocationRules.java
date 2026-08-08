package com.wust.dormitory.allocation;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OptimizedAllocationRules {
    private OptimizedAllocationRules() {
    }

    static void validateRunState(
            OptimizedAllocationRunService.RunState state,
            String currentInputDigest,
            Instant now) {
        if (!"READY".equals(state.status()) || state.submittedAt() != null) {
            throw new BusinessException(
                    "ALLOCATION_RUN_NOT_READY",
                    "优化分配运行已经提交或当前状态不可提交",
                    HttpStatus.CONFLICT);
        }
        if (!state.expiresAt().isAfter(now)) {
            throw new BusinessException(
                    "ALLOCATION_RUN_EXPIRED",
                    "优化分配运行已经过期，请重新生成",
                    HttpStatus.CONFLICT);
        }
        if (!state.inputDigest().equals(currentInputDigest)) {
            throw new BusinessException(
                    "ALLOCATION_RUN_INPUT_CHANGED",
                    "批次、学生或床位数据已经变化，请重新生成候选方案",
                    HttpStatus.CONFLICT);
        }
    }

    static void validateCandidateSet(List<OptimizedAllocationRunService.Candidate> candidates) {
        Set<Long> students = new HashSet<>();
        Set<Long> beds = new HashSet<>();
        for (OptimizedAllocationRunService.Candidate candidate : candidates) {
            if (!students.add(candidate.studentId())) {
                throw new BusinessException(
                        "ALLOCATION_DUPLICATE_STUDENT",
                        "候选方案中同一学生出现多次",
                        HttpStatus.CONFLICT);
            }
            if (!beds.add(candidate.bedId())) {
                throw new BusinessException(
                        "ALLOCATION_DUPLICATE_BED",
                        "候选方案中同一床位被重复分配",
                        HttpStatus.CONFLICT);
            }
        }
    }

    static void requireCompleteTeams(
            List<OptimizedAllocationRunService.Candidate> candidates,
            Map<Long, Integer> expectedTeamSizes) {
        Map<Long, List<OptimizedAllocationRunService.Candidate>> grouped = new HashMap<>();
        candidates.stream()
                .filter(candidate -> candidate.teamId() != null)
                .forEach(candidate -> grouped
                        .computeIfAbsent(candidate.teamId(), ignored -> new ArrayList<>())
                        .add(candidate));
        for (Map.Entry<Long, Integer> entry : expectedTeamSizes.entrySet()) {
            List<OptimizedAllocationRunService.Candidate> members =
                    grouped.getOrDefault(entry.getKey(), List.of());
            if (members.size() != entry.getValue()
                    || members.stream().map(OptimizedAllocationRunService.Candidate::roomId).distinct().count() != 1) {
                throw new BusinessException(
                        "ALLOCATION_TEAM_PARTIAL",
                        "锁定队伍必须完整分配到同一寝室，不能部分成功",
                        HttpStatus.CONFLICT);
            }
        }
    }

    static List<OptimizedAllocationRunService.Candidate> swap(
            OptimizedAllocationRunService.Candidate left,
            OptimizedAllocationRunService.Candidate right) {
        return List.of(
                new OptimizedAllocationRunService.Candidate(
                        left.studentId(), right.bedId(), right.roomId(), left.teamId(), left.score()),
                new OptimizedAllocationRunService.Candidate(
                        right.studentId(), left.bedId(), left.roomId(), right.teamId(), right.score()));
    }
}
