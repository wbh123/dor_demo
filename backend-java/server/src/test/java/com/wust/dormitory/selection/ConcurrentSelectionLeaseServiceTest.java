package com.wust.dormitory.selection;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConcurrentSelectionLeaseServiceTest {
    @Test
    void allTabsOfOneStudentShareOneActiveUserMember() {
        assertEquals(
                "dormitory:selection:active-students",
                ConcurrentSelectionLeaseService.activeUsersKey());
        assertEquals(
                "dormitory:selection:student:42:leases",
                ConcurrentSelectionLeaseService.studentLeasesKey(42L));
        assertTrue(ConcurrentSelectionLeaseService.acquireScriptText()
                .contains("redis.call('zscore', KEYS[1], ARGV[3])"));
        assertTrue(ConcurrentSelectionLeaseService.acquireScriptText()
                .contains("redis.call('zcard', KEYS[2])"));
    }

    @Test
    void acquireRenewAndReleaseScriptsCleanExpiredLeasesAtomically() {
        String acquire = ConcurrentSelectionLeaseService.acquireScriptText();
        String release = ConcurrentSelectionLeaseService.releaseScriptText();

        assertTrue(acquire.contains("redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])"));
        assertTrue(acquire.contains("redis.call('zremrangebyscore', KEYS[2], '-inf', ARGV[1])"));
        assertTrue(acquire.contains("redis.call('zadd', KEYS[2], ARGV[2], ARGV[4])"));
        assertTrue(acquire.contains("redis.call('zadd', KEYS[1], maxExpiry, ARGV[3])"));
        assertTrue(release.contains("redis.call('zrem', KEYS[2], ARGV[3])"));
        assertTrue(release.contains("redis.call('zrem', KEYS[1], ARGV[2])"));
    }

    @Test
    void activeUserCountCleansExpiredMembersBehindServiceBoundary() {
        String countScript = ConcurrentSelectionLeaseService.countScriptText();
        assertTrue(countScript.contains("redis.call('zremrangebyscore', KEYS[1], '-inf', ARGV[1])"));
        assertTrue(countScript.contains("redis.call('zcard', KEYS[1])"));

        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(ConcurrentSelectionLeaseService.activeUsersKey())),
                org.mockito.ArgumentMatchers.<Object[]>any()))
                .thenReturn(7L);

        assertEquals(7, new ConcurrentSelectionLeaseService(redis).activeUsers());
    }

    @Test
    void rejectedLeaseProvidesStableRetryDelay() {
        long earliestExpiry = Instant.now().plusSeconds(17).toEpochMilli();
        var result = ConcurrentSelectionLeaseService.parseResult(
                "0:500:" + earliestExpiry,
                Instant.now().toEpochMilli());

        assertEquals(false, result.accepted());
        assertEquals(500, result.activeUsers());
        assertTrue(result.retryAfterSeconds() >= 16);
    }
}
