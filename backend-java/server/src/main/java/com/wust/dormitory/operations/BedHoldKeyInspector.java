package com.wust.dormitory.operations;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BedHoldKeyInspector {
    public static final String HOLD_PATTERN = "dormitory:batch:*:bed:*:hold";
    private static final Pattern HOLD_KEY = Pattern.compile("^dormitory:batch:(\\d+):bed:(\\d+):hold$");

    private final StringRedisTemplate redis;
    private final NamedParameterJdbcTemplate jdbc;

    public BedHoldKeyInspector(StringRedisTemplate redis, NamedParameterJdbcTemplate jdbc) {
        this.redis = redis;
        this.jdbc = jdbc;
    }

    public Inspection inspect() {
        Set<BatchBed> activePairs = new LinkedHashSet<>(jdbc.query("""
                SELECT scope.batch_id, scope.bed_id
                FROM batch_bed_scope scope
                JOIN selection_batch batch ON batch.id=scope.batch_id
                JOIN bed ON bed.id=scope.bed_id
                WHERE batch.batch_status IN ('PUBLISHED','OPEN','PAUSED')
                  AND bed.operational_status='ENABLED'
                """, Map.of(), (rs, rowNumber) -> new BatchBed(
                rs.getLong("batch_id"),
                rs.getLong("bed_id"))));

        List<String> scannedKeys = scanKeys().stream().sorted().toList();
        Set<String> orphanKeys = new LinkedHashSet<>();
        Set<String> retainedKeys = new LinkedHashSet<>();
        for (String key : scannedKeys) {
            BatchBed parsed = parse(key);
            Long ttl = redis.getExpire(key);
            if (parsed == null || ttl == null || ttl <= 0 || !activePairs.contains(parsed)) {
                orphanKeys.add(key);
            } else {
                retainedKeys.add(key);
            }
        }
        return new Inspection(
                scannedKeys,
                List.copyOf(orphanKeys),
                List.copyOf(retainedKeys));
    }

    private Set<String> scanKeys() {
        Set<String> keys = redis.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> result = new LinkedHashSet<>();
            ScanOptions options = ScanOptions.scanOptions().match(HOLD_PATTERN).count(500).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    result.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return result;
        });
        return keys == null ? Set.of() : keys;
    }

    private BatchBed parse(String key) {
        Matcher matcher = HOLD_KEY.matcher(key == null ? "" : key);
        if (!matcher.matches()) {
            return null;
        }
        return new BatchBed(
                Long.parseLong(matcher.group(1)),
                Long.parseLong(matcher.group(2)));
    }

    private record BatchBed(long batchId, long bedId) {
    }

    public record Inspection(
            List<String> scannedKeys,
            List<String> orphanKeys,
            List<String> retainedKeys) {
    }
}
