package com.wust.dormitory.readiness;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemReadinessServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-17T05:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void returnsReadyWhenEveryCheckerPassesOrOnlyProvidesInfo() {
        SystemReadinessService service = new SystemReadinessService(List.of(
                checker("INFRASTRUCTURE", ReadinessCheckResult.pass(
                        "MYSQL_ACCESS", "INFRASTRUCTURE", "数据库连接", "数据库连接正常", NOW)),
                checker("MOBILE", ReadinessCheckResult.info(
                        "MOBILE_OPTIONAL", "MOBILE", "移动应用", "当前未启用移动管理端", NOW))
        ), CLOCK);

        SystemReadinessReport report = service.check();

        assertEquals(ReadinessOverallStatus.READY, report.overallStatus());
        assertEquals(2, report.summary().total());
        assertEquals(0, report.summary().warnings());
        assertEquals(0, report.summary().blocking());
    }

    @Test
    void returnsReadyWithWarningsWhenAWarningExists() {
        SystemReadinessService service = new SystemReadinessService(List.of(
                checker("STUDENT", ReadinessCheckResult.warning(
                        "STUDENT_NOT_ACTIVATED", "STUDENT", "未激活学生", "有 3 名学生尚未激活账号",
                        "开放前提醒学生完成激活", "/admin/data", NOW))
        ), CLOCK);

        SystemReadinessReport report = service.check();

        assertEquals(ReadinessOverallStatus.READY_WITH_WARNINGS, report.overallStatus());
        assertEquals(1, report.summary().warnings());
        assertEquals(0, report.summary().blocking());
    }

    @Test
    void returnsBlockedWhenBlockingErrorExists() {
        SystemReadinessService service = new SystemReadinessService(List.of(
                checker("BATCH", ReadinessCheckResult.error(
                        "BATCH_PUBLISH_PREFLIGHT_FAILED", "BATCH", "批次发布预检", "当前批次发布预检未通过",
                        true, "修正批次范围后重新检查", "/admin/batches", NOW))
        ), CLOCK);

        SystemReadinessReport report = service.check();

        assertEquals(ReadinessOverallStatus.BLOCKED, report.overallStatus());
        assertEquals(1, report.summary().blocking());
        assertTrue(report.checks().getFirst().blocking());
    }

    @Test
    void isolatesCheckerFailureAndKeepsTheRestOfTheReport() {
        ReadinessChecker broken = new ReadinessChecker() {
            @Override
            public String category() {
                return "MOBILE";
            }

            @Override
            public boolean critical() {
                return false;
            }

            @Override
            public List<ReadinessCheckResult> check(ReadinessContext context) {
                throw new IllegalStateException("simulated failure");
            }
        };
        SystemReadinessService service = new SystemReadinessService(List.of(
                checker("INFRASTRUCTURE", ReadinessCheckResult.pass(
                        "MYSQL_ACCESS", "INFRASTRUCTURE", "数据库连接", "数据库连接正常", NOW)),
                broken
        ), CLOCK);

        SystemReadinessReport report = service.check();

        assertEquals(ReadinessOverallStatus.READY_WITH_WARNINGS, report.overallStatus());
        assertEquals(2, report.checks().size());
        ReadinessCheckResult failed = report.checks().stream()
                .filter(item -> item.code().equals("MOBILE_CHECK_FAILED"))
                .findFirst()
                .orElseThrow();
        assertEquals(ReadinessSeverity.WARNING, failed.severity());
        assertFalse(failed.blocking());
        assertEquals("CHECK_FAILED", failed.status());
    }

    private ReadinessChecker checker(String category, ReadinessCheckResult result) {
        return new ReadinessChecker() {
            @Override
            public String category() {
                return category;
            }

            @Override
            public boolean critical() {
                return false;
            }

            @Override
            public List<ReadinessCheckResult> check(ReadinessContext context) {
                return List.of(result);
            }
        };
    }
}
