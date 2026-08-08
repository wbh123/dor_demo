package com.wust.dormitory.allocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BaselineAllocationPlanner {
    public static final String ALGORITHM_VERSION = "team-first-all-students-v2";

    public AllocationModels.Plan plan(AllocationModels.InputSnapshot snapshot, long randomSeed) {
        List<AllocationModels.BedCandidate> beds = orderedBeds(snapshot.beds(), randomSeed);
        List<AllocationModels.TeamCandidate> teams = orderedTeams(snapshot.lockedTeams(), randomSeed);
        List<AllocationModels.StudentCandidate> remainingStudents = orderedStudents(snapshot.students(), randomSeed);
        Set<Long> usedBeds = new HashSet<>();
        Set<Long> plannedStudents = new HashSet<>();
        List<AllocationModels.AssignmentItem> assignments = new ArrayList<>();
        List<AllocationModels.UnassignedItem> unassigned = new ArrayList<>();

        Map<Long, List<AllocationModels.BedCandidate>> bedsByRoom = new LinkedHashMap<>();
        beds.forEach(bed -> bedsByRoom.computeIfAbsent(bed.roomId(), ignored -> new ArrayList<>()).add(bed));

        for (AllocationModels.TeamCandidate team : teams) {
            List<AllocationModels.StudentCandidate> members = team.members();
            if (members.size() != team.expectedUnassignedCount()) {
                members.forEach(member -> {
                    plannedStudents.add(member.studentId());
                    unassigned.add(AllocationModels.UnassignedItem.of(
                            member, "TEAM_MEMBER_STATE_INVALID", "锁定队伍成员状态不完整"));
                });
                continue;
            }
            List<AllocationModels.BedCandidate> selectedRoomBeds = bedsByRoom.values().stream()
                    .filter(roomBeds -> !roomBeds.isEmpty())
                    .filter(roomBeds -> roomBeds.getFirst().gender().equals(team.gender()))
                    .map(roomBeds -> roomBeds.stream().filter(bed -> !usedBeds.contains(bed.bedId())).toList())
                    .filter(roomBeds -> roomBeds.size() >= members.size())
                    .findFirst()
                    .orElse(List.of());
            if (selectedRoomBeds.isEmpty()) {
                members.forEach(member -> {
                    plannedStudents.add(member.studentId());
                    unassigned.add(AllocationModels.UnassignedItem.of(
                            member, "TEAM_ROOM_CAPACITY_INSUFFICIENT", "没有同一房间可容纳完整锁定队伍"));
                });
                continue;
            }
            for (int index = 0; index < members.size(); index++) {
                AllocationModels.StudentCandidate member = members.get(index);
                AllocationModels.BedCandidate bed = selectedRoomBeds.get(index);
                usedBeds.add(bed.bedId());
                plannedStudents.add(member.studentId());
                assignments.add(AllocationModels.AssignmentItem.of(member, bed, team.teamId(), 100.0));
            }
        }

        Map<String, List<AllocationModels.BedCandidate>> remainingBedsByGender = new HashMap<>();
        beds.stream().filter(bed -> !usedBeds.contains(bed.bedId())).forEach(bed ->
                remainingBedsByGender.computeIfAbsent(bed.gender(), ignored -> new ArrayList<>()).add(bed));
        Map<String, Integer> genderIndex = new HashMap<>();
        for (AllocationModels.StudentCandidate student : remainingStudents) {
            if (plannedStudents.contains(student.studentId())) continue;
            List<AllocationModels.BedCandidate> genderBeds = remainingBedsByGender.getOrDefault(student.gender(), List.of());
            int index = genderIndex.getOrDefault(student.gender(), 0);
            if (index >= genderBeds.size()) {
                unassigned.add(AllocationModels.UnassignedItem.of(
                        student, "NO_AVAILABLE_BED", "没有符合性别和批次范围的剩余床位"));
                plannedStudents.add(student.studentId());
                continue;
            }
            AllocationModels.BedCandidate bed = genderBeds.get(index);
            genderIndex.put(student.gender(), index + 1);
            usedBeds.add(bed.bedId());
            plannedStudents.add(student.studentId());
            assignments.add(AllocationModels.AssignmentItem.of(student, bed, null, 90.0));
        }

        List<Map<String, Object>> studentSnapshot = snapshot.students().stream()
                .sorted(Comparator.comparing(AllocationModels.StudentCandidate::studentNumber)
                        .thenComparingLong(AllocationModels.StudentCandidate::studentId))
                .map(AllocationModels.StudentCandidate::snapshotMap)
                .toList();
        List<Map<String, Object>> bedSnapshot = beds.stream().map(AllocationModels.BedCandidate::snapshotMap).toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("studentCount", studentSnapshot.size());
        summary.put("availableBedCount", bedSnapshot.size());
        summary.put("lockedTeamCount", teams.size());
        summary.put("assignedCount", assignments.size());
        summary.put("unassignedCount", unassigned.size());
        summary.put("allStudentsIncluded", true);
        summary.put("randomSeed", randomSeed);
        summary.put("algorithmVersion", ALGORITHM_VERSION);
        return new AllocationModels.Plan(studentSnapshot, bedSnapshot, assignments, unassigned, summary);
    }

    private List<AllocationModels.BedCandidate> orderedBeds(List<AllocationModels.BedCandidate> beds, long seed) {
        return beds.stream().sorted(Comparator
                .comparing(AllocationModels.BedCandidate::gender)
                .thenComparingLong(bed -> stableOrder(bed.roomId(), seed))
                .thenComparingLong(AllocationModels.BedCandidate::roomId)
                .thenComparingInt(AllocationModels.BedCandidate::positionIndex)
                .thenComparingLong(AllocationModels.BedCandidate::bedId)).toList();
    }

    private List<AllocationModels.TeamCandidate> orderedTeams(List<AllocationModels.TeamCandidate> teams, long seed) {
        return teams.stream().sorted(Comparator
                .comparingLong((AllocationModels.TeamCandidate team) -> stableOrder(team.teamId(), seed))
                .thenComparingLong(AllocationModels.TeamCandidate::teamId)).toList();
    }

    private List<AllocationModels.StudentCandidate> orderedStudents(
            List<AllocationModels.StudentCandidate> students, long seed) {
        return students.stream().sorted(Comparator
                .comparing(AllocationModels.StudentCandidate::gender)
                .thenComparingLong(student -> stableOrder(student.studentId(), seed))
                .thenComparingLong(AllocationModels.StudentCandidate::studentId)).toList();
    }

    private long stableOrder(long id, long seed) {
        return Math.floorMod(id + seed, 100000L);
    }
}
