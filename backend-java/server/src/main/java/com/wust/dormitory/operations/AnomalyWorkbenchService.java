package com.wust.dormitory.operations;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnomalyWorkbenchService {
    private final NamedParameterJdbcTemplate jdbc;
    private final BedHoldKeyInspector bedHoldKeyInspector;

    public AnomalyWorkbenchService(
            NamedParameterJdbcTemplate jdbc,
            BedHoldKeyInspector bedHoldKeyInspector) {
        this.jdbc = jdbc;
        this.bedHoldKeyInspector = bedHoldKeyInspector;
    }

    public List<Map<String, Object>> listAnomalies(String type, String severity) {
        List<Map<String, Object>> result = new ArrayList<>();
        result.addAll(unknownBedResidencies());
        result.addAll(duplicateActiveResidencies());
        result.addAll(staleRoomLocks());
        result.addAll(staleStudentLocks());
        result.addAll(orphanBedHolds());
        return result.stream()
                .filter(item -> matches(type, item.get("type")))
                .filter(item -> matches(severity, item.get("severity")))
                .toList();
    }

    public Map<String, Object> summary() {
        List<Map<String, Object>> anomalies = listAnomalies("ALL", "ALL");
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (Map<String, Object> item : anomalies) {
            byType.merge(String.valueOf(item.get("type")), 1L, Long::sum);
            bySeverity.merge(String.valueOf(item.get("severity")), 1L, Long::sum);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", anomalies.size());
        result.put("byType", byType);
        result.put("bySeverity", bySeverity);
        result.put("blocking", bySeverity.getOrDefault("CRITICAL", 0L));
        return result;
    }

    private List<Map<String, Object>> unknownBedResidencies() {
        return jdbc.queryForList("""
                SELECT assignment.id AS targetId,
                       assignment.student_id AS studentId,
                       assignment.room_id AS roomId,
                       student.student_number AS studentNumber,
                       student.student_name AS studentName,
                       building.building_name AS buildingName,
                       room.room_number AS roomNumber
                FROM room_assignment assignment
                JOIN student ON student.id=assignment.student_id
                JOIN room ON room.id=assignment.room_id
                JOIN dormitory_floor floor ON floor.id=room.floor_id
                JOIN dormitory_building building ON building.id=floor.building_id
                WHERE assignment.assignment_status='ACTIVE'
                  AND assignment.bed_id IS NULL
                ORDER BY assignment.id
                """, Map.of()).stream()
                .map(row -> anomaly(
                        "UNKNOWN_BED_RESIDENCY",
                        "WARNING",
                        row,
                        "学生已有寝室归属但实际床位尚未确认",
                        "管理员进入在住管理核对现实床位；确认前不要将该寝室开放为选床模式"))
                .toList();
    }

    private List<Map<String, Object>> duplicateActiveResidencies() {
        return jdbc.queryForList("""
                SELECT student_id AS targetId,
                       COUNT(*) AS duplicateCount,
                       GROUP_CONCAT(id ORDER BY id) AS assignmentIds
                FROM room_assignment
                WHERE assignment_status='ACTIVE'
                GROUP BY student_id
                HAVING COUNT(*)>1
                """, Map.of()).stream()
                .map(row -> anomaly(
                        "DUPLICATE_ACTIVE_RESIDENCY",
                        "CRITICAL",
                        row,
                        "同一学生存在多条有效在住记录",
                        "暂停相关换寝和调整操作，保留现实有效记录并结束其余记录后重新执行完整性检查"))
                .toList();
    }

    private List<Map<String, Object>> staleRoomLocks() {
        return jdbc.queryForList("""
                SELECT lock_row.room_id AS targetId,
                       lock_row.batch_id AS batchId,
                       batch.batch_status AS batchStatus
                FROM active_batch_room_lock lock_row
                LEFT JOIN selection_batch batch ON batch.id=lock_row.batch_id
                WHERE batch.id IS NULL
                   OR batch.batch_status NOT IN ('PUBLISHED','OPEN','PAUSED')
                """, Map.of()).stream()
                .map(row -> anomaly(
                        "STALE_BATCH_ROOM_LOCK",
                        "ERROR",
                        row,
                        "寝室仍被非活动批次锁定",
                        "确认批次已经结束后释放该寝室活动锁，再重新执行批次预检"))
                .toList();
    }

    private List<Map<String, Object>> staleStudentLocks() {
        return jdbc.queryForList("""
                SELECT lock_row.student_id AS targetId,
                       lock_row.batch_id AS batchId,
                       batch.batch_status AS batchStatus
                FROM active_batch_student_lock lock_row
                LEFT JOIN selection_batch batch ON batch.id=lock_row.batch_id
                WHERE batch.id IS NULL
                   OR batch.batch_status NOT IN ('PUBLISHED','OPEN','PAUSED')
                """, Map.of()).stream()
                .map(row -> anomaly(
                        "STALE_BATCH_STUDENT_LOCK",
                        "ERROR",
                        row,
                        "学生仍被非活动批次锁定",
                        "确认学生没有待完成选择后释放活动学生锁"))
                .toList();
    }

    private List<Map<String, Object>> orphanBedHolds() {
        return bedHoldKeyInspector.inspect().orphanKeys().stream()
                .map(key -> {
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("targetId", key);
                    details.put("redisKey", key);
                    return anomaly(
                            "ORPHAN_BED_HOLD",
                            "WARNING",
                            details,
                            "Redis 中存在不属于活动批次床位范围或没有有效过期时间的临时占用",
                            "先执行 Redis 恢复预检，确认后清理孤立键");
                })
                .toList();
    }

    private Map<String, Object> anomaly(
            String type,
            String severity,
            Map<String, Object> details,
            String message,
            String resolutionHint) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("severity", severity);
        result.put("message", message);
        result.put("resolutionHint", resolutionHint);
        result.put("details", new LinkedHashMap<>(details));
        result.put("targetId", details.get("targetId"));
        return result;
    }

    private boolean matches(String requested, Object actual) {
        return requested == null
                || requested.isBlank()
                || "ALL".equalsIgnoreCase(requested)
                || requested.equalsIgnoreCase(String.valueOf(actual));
    }
}
