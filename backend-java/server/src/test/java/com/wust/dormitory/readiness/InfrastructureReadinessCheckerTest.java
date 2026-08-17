package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InfrastructureReadinessCheckerTest {
    private static final ReadinessContext CONTEXT = new ReadinessContext(Instant.parse("2026-08-17T05:00:00Z"));

    @Test
    void RedisFailureIsBlocking() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        Flyway flyway = mock(Flyway.class, RETURNS_DEEP_STUBS);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(mapper.databaseProbe()).thenReturn(1);
        when(mapper.databaseVersion()).thenReturn("8.4.7");
        when(flyway.info().current()).thenReturn(null);
        when(flyway.info().pending()).thenReturn(new MigrationInfo[0]);
        when(flyway.info().all()).thenReturn(new MigrationInfo[0]);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        List<ReadinessCheckResult> results = new InfrastructureReadinessChecker(mapper, flyway, redis).check(CONTEXT);

        assertTrue(results.stream().anyMatch(item -> item.code().equals("REDIS_RW") && item.blocking()));
    }

    @Test
    void pendingFlywayMigrationIsBlocking() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        Flyway flyway = mock(Flyway.class, RETURNS_DEEP_STUBS);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        MigrationInfo pending = mock(MigrationInfo.class);
        when(mapper.databaseProbe()).thenReturn(1);
        when(mapper.databaseVersion()).thenReturn("8.4.7");
        when(flyway.info().current()).thenReturn(null);
        when(flyway.info().pending()).thenReturn(new MigrationInfo[]{pending});
        when(flyway.info().all()).thenReturn(new MigrationInfo[]{pending});
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        List<ReadinessCheckResult> results = new InfrastructureReadinessChecker(mapper, flyway, redis).check(CONTEXT);

        assertTrue(results.stream().anyMatch(item -> item.code().equals("FLYWAY_PENDING") && item.blocking()));
    }
}
