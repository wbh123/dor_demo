package com.wust.dormitory.allocation;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OptimizedAllocationConstraintValidator {
    public void requireHardConstraints(
            AllocationModels.InputSnapshot snapshot,
            List<OptimizedAllocationRunService.Candidate> candidates) {
        Map<Long, AllocationModels.StudentCandidate> students = new HashMap<>();
        snapshot.students().forEach(student -> students.put(student.studentId(), student));
        Map<Long, AllocationModels.BedCandidate> beds = new HashMap<>();
        snapshot.beds().forEach(bed -> beds.put(bed.bedId(), bed));
        for (OptimizedAllocationRunService.Candidate candidate : candidates) {
            AllocationModels.StudentCandidate student = students.get(candidate.studentId());
            AllocationModels.BedCandidate bed = beds.get(candidate.bedId());
            if (student == null || bed == null
                    || bed.roomId() != candidate.roomId()
                    || !student.gender().equals(bed.gender())) {
                throw new BusinessException(
                        "ALLOCATION_HARD_CONSTRAINT_FAILED",
                        "候选方案不再满足性别、范围、床位状态或唯一性约束",
                        HttpStatus.CONFLICT);
            }
        }
    }

    public Map<Long, Integer> expectedLockedTeamSizes(AllocationModels.InputSnapshot snapshot) {
        Map<Long, Integer> sizes = new HashMap<>();
        snapshot.lockedTeams().forEach(team -> sizes.put(team.teamId(), team.members().size()));
        return sizes;
    }
}
