package com.wust.dormitory.allocation;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AllocationSnapshotReader {
    private final AllocationSnapshotMapper mapper;

    public AllocationSnapshotReader(AllocationSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    public AllocationModels.InputSnapshot read(long batchId) {
        if (mapper.findBatchId(batchId) == null) {
            throw new BusinessException("BATCH_NOT_FOUND", "选寝批次不存在", HttpStatus.NOT_FOUND);
        }
        List<AllocationModels.StudentCandidate> students = mapper.findEligibleStudents(batchId).stream()
                .map(this::student)
                .toList();
        List<AllocationModels.BedCandidate> beds = mapper.findAvailableBeds(batchId).stream()
                .map(this::bed)
                .toList();
        List<AllocationModels.TeamCandidate> teams = teams(mapper.findLockedTeamMembers(batchId));
        return new AllocationModels.InputSnapshot(batchId, students, beds, teams);
    }

    private AllocationModels.StudentCandidate student(Map<String, Object> row) {
        return new AllocationModels.StudentCandidate(
                number(row.get("student_id")),
                String.valueOf(row.get("student_number")),
                String.valueOf(row.get("student_name")),
                String.valueOf(row.get("gender")),
                nullableLong(row.get("major_id")),
                nullableString(row.get("account_status")));
    }

    private AllocationModels.BedCandidate bed(Map<String, Object> row) {
        return new AllocationModels.BedCandidate(
                number(row.get("bed_id")),
                number(row.get("room_id")),
                String.valueOf(row.get("gender")),
                String.valueOf(row.get("building_name")),
                String.valueOf(row.get("room_number")),
                String.valueOf(row.get("bed_code")),
                ((Number) row.get("position_index")).intValue());
    }

    private List<AllocationModels.TeamCandidate> teams(List<Map<String, Object>> rows) {
        Map<Long, List<AllocationModels.TeamMember>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long teamId = number(row.get("team_id"));
            grouped.computeIfAbsent(teamId, ignored -> new ArrayList<>()).add(new AllocationModels.TeamMember(
                    teamId,
                    number(row.get("student_id")),
                    String.valueOf(row.get("student_number")),
                    String.valueOf(row.get("student_name")),
                    String.valueOf(row.get("gender")),
                    String.valueOf(row.get("member_role")),
                    number(row.get("member_order")),
                    ((Number) row.get("assigned")).intValue() == 1));
        }
        List<AllocationModels.TeamCandidate> result = new ArrayList<>();
        grouped.forEach((teamId, members) -> {
            List<AllocationModels.TeamMember> unassigned = members.stream().filter(member -> !member.assigned()).toList();
            long genderCount = unassigned.stream().map(AllocationModels.TeamMember::gender).distinct().count();
            if (!unassigned.isEmpty() && genderCount == 1) {
                result.add(new AllocationModels.TeamCandidate(
                        teamId,
                        unassigned.getFirst().gender(),
                        unassigned.size(),
                        members.stream().map(AllocationModels.TeamMember::student).toList()));
            }
        });
        return List.copyOf(result);
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
