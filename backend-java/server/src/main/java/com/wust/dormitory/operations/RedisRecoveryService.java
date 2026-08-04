package com.wust.dormitory.operations;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RedisRecoveryService {
    private static final List<String> TRANSIENT_PATTERNS = List.of(
            "bed:hold:*",
            "student:hold:*",
            "team:hold:*",
            "dormitory:hold:*"
    );
    private static final String OCCUPANCY_KEY_PREFIX = "dormitory:recovery:room:";

    private final StringRedisTemplate redis;
    private final NamedParameterJdbcTemplate jdbc;

    public RedisRecoveryService(StringRedisTemplate redis, NamedParameterJdbcTemplate jdbc) {
        this.redis = redis;
        this.jdbc = jdbc;
    }

    public Map<String, Object> previewRecovery() {
        return buildPlan(true);
    }

    public synchronized Map<String, Object> executeRecovery() {
        Map<String, Object> plan = buildPlan(false);
        @SuppressWarnings("unchecked")
        List<String> orphanKeys = (List<String>) plan.get("orphanKeys");
        long removedKeys = orphanKeys.isEmpty() ? 0L : redis.delete(orphanKeys);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> roomProjections = (List<Map<String, Object>>) plan.get("roomProjections");
        int recreatedKeys = 0;
        for (Map<String, Object> room : roomProjections) {
            String key = OCCUPANCY_KEY_PREFIX + room.get("roomId") + ":occupancy";
            redis.opsForHash().putAll(key, Map.of(
                    "occupied", String.valueOf(room.get("occupied")),
                    "capacity", String.valueOf(room.get("capacity")),
                    "unknownBedResidents", String.valueOf(room.get("unknownBedResidents")),
                    "recoveredAt", Instant.now().toString()
            ));
            redis.expire(key, Duration.ofHours(24));
            recreatedKeys++;
        }

        Map<String, Object> result = new LinkedHashMap<>(plan);
        result.put("dryRun", false);
        result.put("removedKeys", removedKeys);
        result.put("recreatedKeys", recreatedKeys);
        result.put("executedAt", Instant.now().toString());
        result.put("notice", "临时占用无法从最终数据库可靠重建，恢复只清理失效键并重建房间占用投影");
        return result;
    }

    private Map<String, Object> buildPlan(boolean dryRun) {
        Set<String> transientKeys = new LinkedHashSet<>();
        for (String pattern : TRANSIENT_PATTERNS) {
            Set<String> keys = redis.keys(pattern);
            if (keys != null) {
                transientKeys.addAll(keys);
            }
        }

        Set<Long> activeBedIds = new LinkedHashSet<>(jdbc.queryForList("""
                SELECT scope.bed_id
                FROM batch_bed_scope scope
                JOIN selection_batch batch ON batch.id=scope.batch_id
                JOIN bed ON bed.id=scope.bed_id
                WHERE batch.batch_status IN ('PUBLISHED','OPEN','PAUSED')
                  AND bed.operational_status='ENABLED'
                """, Map.of(), Long.class));

        List<String> orphanKeys = new ArrayList<>();
        List<String> retainedKeys = new ArrayList<>();
        for (String key : transientKeys) {
            Long bedId = parseTrailingLong(key);
            Long ttl = redis.getExpire(key);
            if (bedId == null || !activeBedIds.contains(bedId) || ttl == null || ttl <= 0) {
                orphanKeys.add(key);
            } else {
                retainedKeys.add(key);
            }
        }

        List<Map<String, Object>> roomProjections = jdbc.queryForList("""
                SELECT room.id AS roomId,
                       room.capacity AS capacity,
                       COUNT(assignment.id) AS occupied,
                       SUM(CASE WHEN assignment.id IS NOT NULL AND assignment.bed_id IS NULL THEN 1 ELSE 0 END)
                         AS unknownBedResidents
                FROM room
                LEFT JOIN room_assignment assignment
                  ON assignment.room_id=room.id AND assignment.assignment_status='ACTIVE'
                GROUP BY room.id, room.capacity
                HAVING COUNT(assignment.id)>0
                ORDER BY room.id
                """, Map.of());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dryRun", dryRun);
        result.put("scannedKeys", transientKeys.size());
        result.put("orphanKeys", List.copyOf(orphanKeys));
        result.put("retainedKeys", List.copyOf(retainedKeys));
        result.put("roomProjections", roomProjections);
        result.put("removedKeys", dryRun ? orphanKeys.size() : 0);
        result.put("recreatedKeys", dryRun ? roomProjections.size() : 0);
        result.put("warnings", List.of(
                "Redis只保存临时状态，MySQL在住记录仍是最终事实",
                "服务重启期间丢失的临时床位占用不会自动恢复，学生需要重新选择"
        ));
        return result;
    }

    private Long parseTrailingLong(String key) {
        if (key == null) {
            return null;
        }
        String[] parts = key.split(":");
        for (int index = parts.length - 1; index >= 0; index--) {
            if (parts[index].matches("\\d+")) {
                return Long.valueOf(parts[index]);
            }
        }
        return null;
    }
}
