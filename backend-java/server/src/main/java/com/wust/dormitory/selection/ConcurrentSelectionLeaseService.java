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
public class ConcurrentSelectionLeaseService {
    private static final String ACTIVE_USERS_KEY = "dormitory:selection:active-students";

    private static final String ACQUIRE_SCRIPT = """
            redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])
            redis.call('zremrangebyscore', KEYS[2], '-inf', ARGV[1])
            local active = redis.call('zcard', KEYS[1])
            local existing = redis.call('zscore', KEYS[1], ARGV[3])
            local tabs = redis.call('zcard', KEYS[2])
            if tabs == 0 and existing then
              redis.call('zrem', KEYS[1], ARGV[3])
              active = redis.call('zcard', KEYS[1])
              existing = false
            end
            if tabs == 0 and not existing and active >= tonumber(ARGV[5]) then
              local earliest = redis.call('zrange', KEYS[1], 0, 0, 'WITHSCORES')
              local retryAt = ARGV[2]
              if earliest[2] then retryAt = earliest[2] end
              return '0:' .. active .. ':' .. retryAt
            end
            redis.call('zadd', KEYS[2], ARGV[2], ARGV[4])
            local latest = redis.call('zrevrange', KEYS[2], 0, 0, 'WITHSCORES')
            local maxExpiry = latest[2]
            redis.call('zadd', KEYS[1], maxExpiry, ARGV[3])
            redis.call('pexpire', KEYS[2], math.max(1, maxExpiry - tonumber(ARGV[1])))
            active = redis.call('zcard', KEYS[1])
            return '1:' .. active .. ':' .. maxExpiry
            """;

    private static final String RENEW_SCRIPT = """
            redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])
            redis.call('zremrangebyscore', KEYS[2], '-inf', ARGV[1])
            if not redis.call('zscore', KEYS[2], ARGV[4]) then
              local active = redis.call('zcard', KEYS[1])
              return '-1:' .. active .. ':' .. ARGV[1]
            end
            redis.call('zadd', KEYS[2], ARGV[2], ARGV[4])
            local latest = redis.call('zrevrange', KEYS[2], 0, 0, 'WITHSCORES')
            local maxExpiry = latest[2]
            redis.call('zadd', KEYS[1], maxExpiry, ARGV[3])
            redis.call('pexpire', KEYS[2], math.max(1, maxExpiry - tonumber(ARGV[1])))
            local active = redis.call('zcard', KEYS[1])
            return '1:' .. active .. ':' .. maxExpiry
            """;

    private static final String RELEASE_SCRIPT = """
            redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])
            redis.call('zremrangebyscore', KEYS[2], '-inf', ARGV[1])
            redis.call('zrem', KEYS[2], ARGV[3])
            local latest = redis.call('zrevrange', KEYS[2], 0, 0, 'WITHSCORES')
            if latest[2] then
              local maxExpiry = latest[2]
              redis.call('zadd', KEYS[1], maxExpiry, ARGV[2])
              redis.call('pexpire', KEYS[2], math.max(1, maxExpiry - tonumber(ARGV[1])))
            else
              redis.call('zrem', KEYS[1], ARGV[2])
              redis.call('del', KEYS[2])
            end
            local active = redis.call('zcard', KEYS[1])
            return '1:' .. active .. ':' .. ARGV[1]
            """;

    private final StringRedisTemplate redis;

    public ConcurrentSelectionLeaseService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Lease acquire(long studentId, int maxUsers, Duration ttl) {
        if (maxUsers < 1) {
            throw new BusinessException(
                    "CONCURRENT_SELECTION_LIMIT_INVALID",
                    "学校选寝并发上限必须大于零");
        }
        requireTtl(ttl);
        String token = UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();
        long expiresAt = now + ttl.toMillis();
        LeaseScriptResult result = execute(
                ACQUIRE_SCRIPT,
                studentId,
                now,
                expiresAt,
                token,
                maxUsers);
        if (!result.accepted()) {
            throw new ConcurrentSelectionLimitException(
                    result.activeUsers(),
                    result.retryAfterSeconds());
        }
        return new Lease(token, Instant.ofEpochMilli(expiresAt), result.activeUsers());
    }

    public Lease renew(long studentId, String token, Duration ttl) {
        requiredToken(token);
        requireTtl(ttl);
        long now = Instant.now().toEpochMilli();
        long expiresAt = now + ttl.toMillis();
        LeaseScriptResult result = execute(
                RENEW_SCRIPT,
                studentId,
                now,
                expiresAt,
                token,
                Integer.MAX_VALUE);
        if (!result.accepted()) {
            throw new BusinessException(
                    "CONCURRENT_SELECTION_LEASE_EXPIRED",
                    "当前选寝访问凭证已经过期，请重新进入选寝页面",
                    HttpStatus.CONFLICT);
        }
        return new Lease(token, Instant.ofEpochMilli(expiresAt), result.activeUsers());
    }

    public int release(long studentId, String token) {
        requiredToken(token);
        long now = Instant.now().toEpochMilli();
        LeaseScriptResult result = execute(
                RELEASE_SCRIPT,
                studentId,
                now,
                now,
                token,
                Integer.MAX_VALUE);
        return result.activeUsers();
    }

    private LeaseScriptResult execute(
            String scriptText,
            long studentId,
            long now,
            long expiresAt,
            String token,
            int maxUsers) {
        DefaultRedisScript<String> script = new DefaultRedisScript<>(scriptText, String.class);
        try {
            String raw = redis.execute(
                    script,
                    List.of(activeUsersKey(), studentLeasesKey(studentId)),
                    String.valueOf(now),
                    String.valueOf(expiresAt),
                    String.valueOf(studentId),
                    token,
                    String.valueOf(maxUsers));
            if (raw == null) {
                throw redisUnavailable();
            }
            return parseResult(raw, now);
        } catch (RedisConnectionFailureException exception) {
            throw redisUnavailable();
        }
    }

    static LeaseScriptResult parseResult(String raw, long nowMillis) {
        String[] parts = raw.split(":", -1);
        if (parts.length != 3) {
            throw new BusinessException(
                    "CONCURRENT_SELECTION_LEASE_RESPONSE_INVALID",
                    "并发控制服务返回了无法识别的结果",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        int status = Integer.parseInt(parts[0]);
        int activeUsers = Integer.parseInt(parts[1]);
        long retryAt = Math.round(Double.parseDouble(parts[2]));
        long retryAfterSeconds = Math.max(1L, (retryAt - nowMillis + 999L) / 1000L);
        return new LeaseScriptResult(status == 1, activeUsers, retryAfterSeconds);
    }

    static String activeUsersKey() {
        return ACTIVE_USERS_KEY;
    }

    static String studentLeasesKey(long studentId) {
        return "dormitory:selection:student:" + studentId + ":leases";
    }

    static String acquireScriptText() {
        return ACQUIRE_SCRIPT;
    }

    static String releaseScriptText() {
        return RELEASE_SCRIPT;
    }

    private void requireTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new BusinessException(
                    "CONCURRENT_SELECTION_LEASE_TTL_INVALID",
                    "选寝访问凭证有效期必须大于零");
        }
    }

    private void requiredToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(
                    "CONCURRENT_SELECTION_LEASE_TOKEN_REQUIRED",
                    "缺少选寝访问凭证");
        }
    }

    private BusinessException redisUnavailable() {
        return new BusinessException(
                "CONCURRENT_SELECTION_REDIS_UNAVAILABLE",
                "并发控制服务暂时不可用，当前不能进入或确认选寝，请稍后重试",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    public record Lease(String token, Instant expiresAt, int activeUsers) {
    }

    record LeaseScriptResult(boolean accepted, int activeUsers, long retryAfterSeconds) {
    }

    public static final class ConcurrentSelectionLimitException extends BusinessException {
        private final int activeUsers;
        private final long retryAfterSeconds;

        ConcurrentSelectionLimitException(int activeUsers, long retryAfterSeconds) {
            super(
                    "CONCURRENT_SELECTION_LIMIT_REACHED",
                    "当前进入选寝的学生人数已达到学校上限，请约"
                            + retryAfterSeconds + "秒后重试",
                    HttpStatus.TOO_MANY_REQUESTS);
            this.activeUsers = activeUsers;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public int getActiveUsers() {
            return activeUsers;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }
}
