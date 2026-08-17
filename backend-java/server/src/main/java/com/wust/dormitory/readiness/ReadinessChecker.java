package com.wust.dormitory.readiness;

import java.util.List;

public interface ReadinessChecker {
    String category();

    default boolean critical() {
        return false;
    }

    List<ReadinessCheckResult> check(ReadinessContext context);
}
