package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class InfrastructureReadinessChecker implements ReadinessChecker {
    private final SystemReadinessMapper mapper;
    private final Flyway flyway;
    private final StringRedisTemplate redis;

    public InfrastructureReadinessChecker(SystemReadinessMapper mapper, Flyway flyway, StringRedisTemplate redis) {
        this.mapper = mapper;
        this.flyway = flyway;
        this.redis = redis;
    }

    @Override
    public String category() {
        return "INFRASTRUCTURE";
    }

    @Override
    public boolean critical() {
        return true;
    }

    @Override
    public List<ReadinessCheckResult> check(ReadinessContext context) {
        List<ReadinessCheckResult> results = new ArrayList<>();
        boolean databaseAvailable = mapper.databaseProbe() == 1;
        results.add(databaseAvailable
                ? ReadinessCheckResult.of("MYSQL_ACCESS", category(), "数据库连接", ReadinessSeverity.PASS,
                        false, "PASSED", "MySQL 可以正常访问。", Map.of("available", true), null, null, context.checkedAt())
                : ReadinessCheckResult.error("MYSQL_ACCESS", category(), "数据库连接", "MySQL 当前不可访问。",
                        true, "检查数据库地址、账号和网络配置", null, context.checkedAt()));
        if (databaseAvailable) {
            String version = mapper.databaseVersion();
            boolean expected = version != null && version.startsWith("8.4");
            results.add(ReadinessCheckResult.of("MYSQL_VERSION", category(), "数据库版本",
                    expected ? ReadinessSeverity.PASS : ReadinessSeverity.WARNING, false,
                    expected ? "PASSED" : "ATTENTION",
                    expected ? "MySQL 运行版本符合 8.4 基线。" : "MySQL 运行版本与推荐的 8.4 基线不同。",
                    Map.of("actualVersion", version == null ? "unknown" : version, "expectedVersion", "8.4.x"),
                    expected ? null : "在试点前确认当前 MySQL 版本兼容性", null, context.checkedAt()));
            MigrationInfo current = flyway.info().current();
            MigrationInfo[] pending = flyway.info().pending();
            String currentVersion = current == null || current.getVersion() == null ? "none" : current.getVersion().getVersion();
            String highest = currentVersion;
            for (MigrationInfo info : flyway.info().all()) {
                if (info.getVersion() != null && (highest.equals("none") || compareVersion(info.getVersion().getVersion(), highest) > 0)) {
                    highest = info.getVersion().getVersion();
                }
            }
            results.add(ReadinessCheckResult.of("FLYWAY_VERSION", category(), "数据库迁移版本", ReadinessSeverity.PASS,
                    false, "PASSED", "已读取当前数据库与程序迁移版本。",
                    Map.of("currentVersion", currentVersion, "highestApplicationVersion", highest), null, null, context.checkedAt()));
            results.add(pending.length == 0
                    ? ReadinessCheckResult.of("FLYWAY_PENDING", category(), "待执行数据库迁移", ReadinessSeverity.PASS,
                            false, "PASSED", "没有待执行的正式 Flyway 迁移。", Map.of("pendingCount", 0), null, null, context.checkedAt())
                    : ReadinessCheckResult.of("FLYWAY_PENDING", category(), "待执行数据库迁移", ReadinessSeverity.ERROR,
                            true, "FAILED", "存在 " + pending.length + " 个待执行数据库迁移。",
                            Map.of("pendingCount", pending.length), "完成数据库迁移后再开放系统", null, context.checkedAt()));
        }
        results.add(redisReadWrite(context));
        return results;
    }

    private ReadinessCheckResult redisReadWrite(ReadinessContext context) {
        String key = "dormitory:readiness:health:" + UUID.randomUUID();
        String value = UUID.randomUUID().toString();
        try {
            redis.opsForValue().set(key, value, Duration.ofSeconds(10));
            String actual = redis.opsForValue().get(key);
            if (!value.equals(actual)) throw new IllegalStateException("read-back mismatch");
            return ReadinessCheckResult.of("REDIS_RW", category(), "Redis 读写", ReadinessSeverity.PASS,
                    false, "PASSED", "Redis 独立短时健康检查键读写正常。",
                    Map.of("ttlSeconds", 10), null, null, context.checkedAt());
        } catch (RuntimeException exception) {
            return ReadinessCheckResult.of("REDIS_RW", category(), "Redis 读写", ReadinessSeverity.ERROR,
                    true, "FAILED", "Redis 当前无法完成安全的短时读写检查。",
                    Map.of("requiredBySelectionRuntime", true), "检查 Redis 连接与运行状态后重新体检", null, context.checkedAt());
        } finally {
            try {
                redis.delete(key);
            } catch (RuntimeException ignored) {
                // The key has a short TTL and belongs exclusively to readiness checking.
            }
        }
    }

    private int compareVersion(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int av = i < a.length ? parse(a[i]) : 0;
            int bv = i < b.length ? parse(b[i]) : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private int parse(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9].*$", ""));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
