package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InfrastructureReadinessRedis74IntegrationTest {
    @Test
    void validatesRedis74AvailableAndUnavailableWithoutChangingExistingData() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException exception) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable, "Docker 不可用，跳过 Redis 7.4 上线体检验证");

        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
        redis.start();
        String host = redis.getHost();
        int port = redis.getMappedPort(6379);

        LettuceConnectionFactory liveFactory = connectionFactory(host, port);
        StringRedisTemplate liveTemplate = template(liveFactory);
        liveTemplate.opsForValue().set("readiness:preserve", "keep-me");

        InfrastructureReadinessChecker checker = new InfrastructureReadinessChecker(
                healthyDatabaseMapper(), emptyFlyway(), liveTemplate);
        ReadinessCheckResult available = redisResult(checker.check(
                new ReadinessContext(Instant.parse("2026-08-17T05:00:00Z"))));

        assertEquals(ReadinessSeverity.PASS, available.severity());
        assertFalse(available.blocking());
        assertEquals(10, available.evidence().get("ttlSeconds"));
        assertEquals("keep-me", liveTemplate.opsForValue().get("readiness:preserve"));

        liveFactory.destroy();
        redis.stop();

        LettuceConnectionFactory deadFactory = connectionFactory(host, port);
        StringRedisTemplate deadTemplate = template(deadFactory);
        ReadinessCheckResult unavailable = redisResult(new InfrastructureReadinessChecker(
                healthyDatabaseMapper(), emptyFlyway(), deadTemplate).check(
                new ReadinessContext(Instant.parse("2026-08-17T05:01:00Z"))));

        assertEquals(ReadinessSeverity.ERROR, unavailable.severity());
        assertTrue(unavailable.blocking());
        assertEquals(Boolean.TRUE, unavailable.evidence().get("requiredBySelectionRuntime"));
        deadFactory.destroy();
    }

    private ReadinessCheckResult redisResult(List<ReadinessCheckResult> results) {
        return results.stream()
                .filter(item -> item.code().equals("REDIS_RW"))
                .findFirst()
                .orElseThrow();
    }

    private SystemReadinessMapper healthyDatabaseMapper() {
        SystemReadinessMapper mapper = mock(SystemReadinessMapper.class);
        when(mapper.databaseProbe()).thenReturn(1);
        when(mapper.databaseVersion()).thenReturn("8.4.7");
        return mapper;
    }

    private Flyway emptyFlyway() {
        Flyway flyway = mock(Flyway.class, RETURNS_DEEP_STUBS);
        when(flyway.info().current()).thenReturn(null);
        when(flyway.info().pending()).thenReturn(new MigrationInfo[0]);
        when(flyway.info().all()).thenReturn(new MigrationInfo[0]);
        return flyway;
    }

    private LettuceConnectionFactory connectionFactory(String host, int port) {
        RedisStandaloneConfiguration server = new RedisStandaloneConfiguration(host, port);
        LettuceClientConfiguration client = LettuceClientConfiguration.builder()
                .commandTimeout(Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ZERO)
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(server, client);
        factory.afterPropertiesSet();
        factory.start();
        return factory;
    }

    private StringRedisTemplate template(LettuceConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        return template;
    }
}
