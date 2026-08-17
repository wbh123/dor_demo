package com.wust.dormitory.readiness;

final class AllocationCapabilityReadiness {
    private AllocationCapabilityReadiness() {
    }

    static boolean hasUsablePath(boolean selfSelection, boolean unifiedAllocation) {
        return selfSelection || unifiedAllocation;
    }
}
