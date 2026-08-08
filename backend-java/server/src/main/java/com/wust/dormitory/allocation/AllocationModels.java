package com.wust.dormitory.allocation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AllocationModels {
    private AllocationModels() {
    }

    public record StudentCandidate(
            long studentId,
            String studentNumber,
            String studentName,
            String gender,
            Long majorId,
            String accountStatus) {
        Map<String, Object> snapshotMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("student_id", studentId);
            map.put("student_number", studentNumber);
            map.put("student_name", studentName);
            map.put("gender", gender);
            map.put("major_id", majorId);
            map.put("account_status", accountStatus);
            return map;
        }
    }

    public record TeamMember(
            long teamId,
            long studentId,
            String studentNumber,
            String studentName,
            String gender,
            String memberRole,
            long memberOrder,
            boolean assigned) {
        StudentCandidate student() {
            return new StudentCandidate(studentId, studentNumber, studentName, gender, null, null);
        }
    }

    public record TeamCandidate(
            long teamId,
            String gender,
            int expectedUnassignedCount,
            List<StudentCandidate> members) {
    }

    public record BedCandidate(
            long bedId,
            long roomId,
            String gender,
            String buildingName,
            String roomNumber,
            String bedCode,
            int positionIndex) {
        String displayName() {
            return buildingName + " " + roomNumber + "-" + bedCode;
        }

        Map<String, Object> snapshotMap() {
            return Map.of(
                    "bedId", bedId,
                    "roomId", roomId,
                    "gender", gender,
                    "buildingName", buildingName,
                    "roomNumber", roomNumber,
                    "bedCode", bedCode);
        }
    }

    public record InputSnapshot(
            long batchId,
            List<StudentCandidate> students,
            List<BedCandidate> beds,
            List<TeamCandidate> lockedTeams) {
    }

    public record AssignmentItem(
            long studentId,
            String studentNumber,
            String studentName,
            long bedId,
            long roomId,
            String room,
            Long teamId,
            double score) {
        static AssignmentItem of(StudentCandidate student, BedCandidate bed, Long teamId, double score) {
            return new AssignmentItem(
                    student.studentId(), student.studentNumber(), student.studentName(),
                    bed.bedId(), bed.roomId(), bed.displayName(), teamId, score);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("studentId", studentId);
            map.put("studentNumber", studentNumber);
            map.put("studentName", studentName);
            map.put("bedId", bedId);
            map.put("roomId", roomId);
            map.put("room", room);
            map.put("teamId", teamId);
            map.put("score", score);
            return map;
        }
    }

    public record UnassignedItem(
            long studentId,
            String studentNumber,
            String studentName,
            String failureCode,
            String failureReason) {
        static UnassignedItem of(StudentCandidate student, String code, String reason) {
            return new UnassignedItem(student.studentId(), student.studentNumber(), student.studentName(), code, reason);
        }

        public Map<String, Object> toMap() {
            return Map.of(
                    "studentId", studentId,
                    "studentNumber", studentNumber,
                    "studentName", studentName,
                    "failureCode", failureCode,
                    "failureReason", failureReason);
        }
    }

    public record Plan(
            List<Map<String, Object>> studentSnapshot,
            List<Map<String, Object>> bedSnapshot,
            List<AssignmentItem> assignments,
            List<UnassignedItem> unassigned,
            Map<String, Object> summary) {
        public Map<String, Object> toMap() {
            return Map.of(
                    "students", studentSnapshot,
                    "beds", bedSnapshot,
                    "assignments", assignments.stream().map(AssignmentItem::toMap).toList(),
                    "unassigned", unassigned.stream().map(UnassignedItem::toMap).toList(),
                    "summary", summary);
        }
    }
}
