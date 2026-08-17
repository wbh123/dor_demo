package com.wust.dormitory.readiness;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SystemReadinessService {
    private final List<ReadinessChecker> checkers;
    private final Clock clock;

    @Autowired
    public SystemReadinessService(List<ReadinessChecker> checkers) {
        this(checkers, Clock.systemUTC());
    }

    SystemReadinessService(List<ReadinessChecker> checkers, Clock clock) {
        this.checkers = List.copyOf(checkers);
        this.clock = clock;
    }

    public SystemReadinessReport check() {
        Instant checkedAt = clock.instant();
        ReadinessContext context = new ReadinessContext(checkedAt);
        List<ReadinessCheckResult> results = new ArrayList<>();
        for (ReadinessChecker checker : checkers) {
            try {
                results.addAll(checker.check(context));
            } catch (RuntimeException exception) {
                boolean blocking = checker.critical();
                ReadinessSeverity severity = blocking ? ReadinessSeverity.ERROR : ReadinessSeverity.WARNING;
                results.add(ReadinessCheckResult.of(
                        checker.category() + "_CHECK_FAILED",
                        checker.category(),
                        "检查执行失败",
                        severity,
                        blocking,
                        "CHECK_FAILED",
                        "该检查暂时无法完成，其他检查结果仍然有效。",
                        java.util.Map.of("exceptionType", exception.getClass().getSimpleName()),
                        "检查相关服务状态后重新执行上线体检",
                        null,
                        checkedAt));
            }
        }
        results.sort(Comparator.comparing(ReadinessCheckResult::category)
                .thenComparing(ReadinessCheckResult::code));
        int passed = (int) results.stream().filter(item -> item.severity() == ReadinessSeverity.PASS).count();
        int info = (int) results.stream().filter(item -> item.severity() == ReadinessSeverity.INFO).count();
        int warnings = (int) results.stream().filter(item -> item.severity() == ReadinessSeverity.WARNING).count();
        int errors = (int) results.stream().filter(item -> item.severity() == ReadinessSeverity.ERROR).count();
        int blocking = (int) results.stream().filter(ReadinessCheckResult::blocking).count();
        ReadinessOverallStatus overall = blocking > 0
                ? ReadinessOverallStatus.BLOCKED
                : (warnings > 0 || errors > 0 ? ReadinessOverallStatus.READY_WITH_WARNINGS : ReadinessOverallStatus.READY);
        List<String> categories = new ArrayList<>(new LinkedHashSet<>(
                results.stream().map(ReadinessCheckResult::category).toList()));
        return new SystemReadinessReport(
                overall,
                checkedAt,
                new SystemReadinessSummary(results.size(), passed, info, warnings, errors, blocking),
                List.copyOf(categories),
                List.copyOf(results));
    }
}
