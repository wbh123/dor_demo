package com.wust.dormitory.selection;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BedHoldService {
    private final StringRedisTemplate redis;

    public BedHoldService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public HoldResult hold(long batchId, long bedId, long studentId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        String value = "S:" + studentId + ":" + token;
        try {
            Boolean success = redis.opsForValue().setIfAbsent(key(batchId, bedId), value, ttl);
            if (!Boolean.TRUE.equals(success)) {
                throw new BusinessException("BED_ALREADY_HELD", "床位刚刚被其他学生临时占用", HttpStatus.CONFLICT);
            }
            return new HoldResult(token, Instant.now().plus(ttl));
        } catch (RedisConnectionFailureException exception) {
            throw new BusinessException("REDIS_UNAVAILABLE", "临时占用服务不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public HoldResult holdTeam(long batchId, List<Long> bedIds, long teamId, Duration ttl) {
        if (bedIds.isEmpty()) {
            throw new BusinessException("BED_REQUIRED", "至少选择一个床位");
        }
        String token = UUID.randomUUID().toString();
        String value = "T:" + teamId + ":" + token;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>("""
                for i,key in ipairs(KEYS) do
                  if redis.call('exists', key) == 1 then return 0 end
                end
                for i,key in ipairs(KEYS) do
                  redis.call('psetex', key, ARGV[2], ARGV[1])
                end
                return 1
                """, Long.class);
        try {
            Long result = redis.execute(script, bedIds.stream().map(id -> key(batchId, id)).toList(),
                    value, String.valueOf(ttl.toMillis()));
            if (!Long.valueOf(1).equals(result)) {
                throw new BusinessException("BED_ALREADY_HELD", "队伍所选床位中已有床位被占用", HttpStatus.CONFLICT);
            }
            return new HoldResult(token, Instant.now().plus(ttl));
        } catch (RedisConnectionFailureException exception) {
            throw new BusinessException("REDIS_UNAVAILABLE", "临时占用服务不可用，请稍后重试", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    public boolean validateStudent(long batchId, long bedId, long studentId, String token) {
        return ("S:" + studentId + ":" + token).equals(redis.opsForValue().get(key(batchId, bedId)));
    }

    public boolean validateTeam(long batchId, long bedId, long teamId, String token) {
        return ("T:" + teamId + ":" + token).equals(redis.opsForValue().get(key(batchId, bedId)));
    }

    public String current(long batchId, long bedId) {
        return redis.opsForValue().get(key(batchId, bedId));
    }

    public void releaseStudent(long batchId, long bedId, long studentId, String token) {
        release(List.of(key(batchId, bedId)), "S:" + studentId + ":" + token);
    }

    public void releaseTeam(long batchId, List<Long> bedIds, long teamId, String token) {
        release(bedIds.stream().map(id -> key(batchId, id)).toList(), "T:" + teamId + ":" + token);
    }

    private void release(List<String> keys, String expectedValue) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>("""
                for i,key in ipairs(KEYS) do
                  local value = redis.call('get', key)
                  if value and value ~= ARGV[1] then return 0 end
                end
                for i,key in ipairs(KEYS) do
                  if redis.call('get', key) == ARGV[1] then redis.call('del', key) end
                end
                return 1
                """, Long.class);
        redis.execute(script, keys, expectedValue);
    }

    private String key(long batchId, long bedId) {
        return "dormitory:batch:" + batchId + ":bed:" + bedId + ":hold";
    }

    public record HoldResult(String token, Instant expiresAt) {
    }
}
