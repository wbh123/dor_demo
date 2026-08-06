package com.wust.dormitory.operations;

import com.wust.dormitory.allocation.AdminAllocationService;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OperationsService {
    private final NamedParameterJdbcTemplate jdbc;
    private final RedisConnectionFactory redisConnectionFactory;
    private final AdminAllocationService allocationService;

    public OperationsService(
            NamedParameterJdbcTemplate jdbc,
            RedisConnectionFactory redisConnectionFactory,
            AdminAllocationService allocationService) {
        this.jdbc = jdbc;
        this.redisConnectionFactory = redisConnectionFactory;
        this.allocationService = allocationService;
    }

    public Map<String, Object> overview() {
        long totalBeds = scalar("SELECT COUNT(*) FROM bed", Map.of());
        long enabledBeds = scalar("SELECT COUNT(*) FROM bed WHERE operational_status='ENABLED'", Map.of());
        long occupiedBeds = scalar("""
                SELECT COUNT(DISTINCT occupied.bed_id)
                FROM (
                    SELECT bed_id
                    FROM room_assignment
                    WHERE assignment_status='ACTIVE' AND bed_id IS NOT NULL
                    UNION ALL
                    SELECT bed_id
                    FROM bed_assignment
                    WHERE assignment_status='ACTIVE' AND bed_id IS NOT NULL
                ) occupied
                """, Map.of());
        long activeResidents = scalar("""
                SELECT COUNT(DISTINCT occupied.student_id)
                FROM (
                    SELECT student_id
                    FROM room_assignment
                    WHERE assignment_status='ACTIVE'
                    UNION ALL
                    SELECT student_id
                    FROM bed_assignment
                    WHERE assignment_status='ACTIVE'
                ) occupied
                """, Map.of());
        long unselectedStudents = scalar("""
                SELECT COUNT(*)
                FROM batch_student_eligibility eligibility
                JOIN selection_batch batch ON batch.id=eligibility.batch_id
                WHERE eligibility.eligibility_status='ELIGIBLE'
                  AND batch.batch_status IN ('PUBLISHED','OPEN','PAUSED','CLOSED')
                  AND NOT EXISTS (
                    SELECT 1 FROM room_assignment residency
                    WHERE residency.student_id=eligibility.student_id
                      AND residency.assignment_status='ACTIVE'
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM bed_assignment assignment
                    WHERE assignment.student_id=eligibility.student_id
                      AND assignment.assignment_status='ACTIVE'
                  )
                """, Map.of());
        long manualAdjustments = scalar("""
                SELECT COUNT(*) FROM room_assignment_history
                WHERE event_type IN ('BED_CHANGED','ROOM_CHANGED')
                """, Map.of());
        long pendingRoomChanges = tableExists("room_change_request")
                ? scalar("SELECT COUNT(*) FROM room_change_request WHERE request_status='PENDING'", Map.of())
                : 0L;
        double utilization = enabledBeds == 0 ? 0.0 : occupiedBeds * 100.0 / enabledBeds;

        Map<String, Object> bedUtilization = new LinkedHashMap<>();
        bedUtilization.put("totalBeds", totalBeds);
        bedUtilization.put("enabledBeds", enabledBeds);
        bedUtilization.put("occupiedBeds", occupiedBeds);
        bedUtilization.put("activeResidents", activeResidents);
        bedUtilization.put("rate", Math.round(utilization * 100.0) / 100.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bedUtilization", bedUtilization);
        result.put("unselectedStudents", unselectedStudents);
        result.put("manualAdjustments", manualAdjustments);
        result.put("pendingRoomChanges", pendingRoomChanges);
        result.put("activeBatches", scalar("""
                SELECT COUNT(*) FROM selection_batch
                WHERE batch_status IN ('PUBLISHED','OPEN','PAUSED')
                """, Map.of()));
        result.put("unknownBedResidents", scalar("""
                SELECT COUNT(*) FROM room_assignment
                WHERE assignment_status='ACTIVE' AND bed_id IS NULL
                """, Map.of()));
        return result;
    }

    public Map<String, Object> health() {
        boolean databaseAvailable = databaseAvailable();
        boolean redisAvailable = redisAvailable();
        List<Map<String, Object>> slowQueryCandidates = databaseAvailable
                ? slowQueryCandidates()
                : List.of();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("databaseAvailable", databaseAvailable);
        result.put("redisAvailable", redisAvailable);
        result.put("slowQueryCandidates", slowQueryCandidates);
        result.put("activeRoomLocks", databaseAvailable ? scalar("SELECT COUNT(*) FROM active_batch_room_lock", Map.of()) : 0L);
        result.put("activeStudentLocks", databaseAvailable ? scalar("SELECT COUNT(*) FROM active_batch_student_lock", Map.of()) : 0L);
        result.put("unconfirmedResidencies", databaseAvailable ? scalar("""
                SELECT COUNT(*) FROM room_assignment
                WHERE assignment_status='ACTIVE' AND bed_id IS NULL
                """, Map.of()) : 0L);
        result.put("healthy", databaseAvailable && redisAvailable);
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> optimizedPreview(long batchId, long randomSeed) {
        Map<String, Object> plan = allocationService.preview(batchId, randomSeed);
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) plan.getOrDefault("assignments", List.of());
        List<Map<String, Object>> unassigned = (List<Map<String, Object>>) plan.getOrDefault("unassigned", List.of());
        List<Double> scores = assignments.stream()
                .map(item -> ((Number) item.getOrDefault("score", 0.0)).doubleValue())
                .toList();
        double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double minimum = scores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double maximum = scores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double variance = scores.stream()
                .mapToDouble(score -> Math.pow(score - average, 2))
                .average()
                .orElse(0.0);
        double standardDeviation = Math.sqrt(variance);
        double fairness = average <= 0.0 ? 0.0 : Math.max(0.0, 100.0 - standardDeviation);

        Map<String, Object> fairnessMetrics = new LinkedHashMap<>();
        fairnessMetrics.put("averageScore", round(average));
        fairnessMetrics.put("minimumScore", round(minimum));
        fairnessMetrics.put("maximumScore", round(maximum));
        fairnessMetrics.put("standardDeviation", round(standardDeviation));
        fairnessMetrics.put("fairness", round(fairness));
        fairnessMetrics.put("assignedCount", assignments.size());
        fairnessMetrics.put("unassignedCount", unassigned.size());

        Map<String, Object> result = new LinkedHashMap<>(plan);
        result.put("fairness", fairnessMetrics);
        result.put("algorithm", "baseline-team-first-with-fairness-evaluation");
        result.put("notice", "当前为可解释预演与公平性评估，不写入正式分配结果");
        return result;
    }

    private boolean databaseAvailable() {
        try {
            Integer value = jdbc.getJdbcTemplate().queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private boolean redisAvailable() {
        try (var connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return pong != null && "PONG".equalsIgnoreCase(pong);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private List<Map<String, Object>> slowQueryCandidates() {
        List<Map<String, Object>> tables = jdbc.queryForList("""
                SELECT TABLE_NAME AS table_name,
                       TABLE_ROWS AS estimated_rows,
                       DATA_LENGTH+INDEX_LENGTH AS total_bytes
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE()
                ORDER BY TABLE_ROWS DESC, DATA_LENGTH+INDEX_LENGTH DESC
                LIMIT 8
                """, Map.of());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> table : tables) {
            Map<String, Object> item = new LinkedHashMap<>(table);
            item.put("recommendation", "结合慢查询日志检查过滤字段、排序字段与复合索引");
            result.add(item);
        }
        return result;
    }

    private boolean tableExists(String tableName) {
        return scalar("""
                SELECT COUNT(*) FROM information_schema.TABLES
                WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=:tableName
                """, Map.of("tableName", tableName)) > 0;
    }

    private long scalar(String sql, Map<String, ?> parameters) {
        Long value = jdbc.queryForObject(sql, parameters, Long.class);
        return value == null ? 0L : value;
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
