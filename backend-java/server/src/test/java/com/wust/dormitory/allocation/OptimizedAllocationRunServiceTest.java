package com.wust.dormitory.allocation;

import com.wust.dormitory.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OptimizedAllocationRunServiceTest {
    @Test
    void commitRequiresReadyUnexpiredUnsubmittedUnchangedRun() {
        Instant now = Instant.parse("2026-08-05T03:00:00Z");
        assertDoesNotThrow(() -> OptimizedAllocationRunService.validateRunState(
                new OptimizedAllocationRunService.RunState(
                        "READY", now.plusSeconds(60), null, "digest-1"),
                "digest-1",
                now));
        assertThrows(BusinessException.class, () -> OptimizedAllocationRunService.validateRunState(
                new OptimizedAllocationRunService.RunState(
                        "READY", now.minusSeconds(1), null, "digest-1"),
                "digest-1",
                now));
        assertThrows(BusinessException.class, () -> OptimizedAllocationRunService.validateRunState(
                new OptimizedAllocationRunService.RunState(
                        "SUBMITTED", now.plusSeconds(60), now, "digest-1"),
                "digest-1",
                now));
        assertThrows(BusinessException.class, () -> OptimizedAllocationRunService.validateRunState(
                new OptimizedAllocationRunService.RunState(
                        "READY", now.plusSeconds(60), null, "digest-1"),
                "digest-2",
                now));
    }

    @Test
    void candidateSetRejectsDuplicateStudentsBedsAndPartialTeams() {
        var one = new OptimizedAllocationRunService.Candidate(1, 11, 101, null, 90.0);
        assertThrows(BusinessException.class, () -> OptimizedAllocationRunService.validateCandidateSet(
                List.of(one, new OptimizedAllocationRunService.Candidate(1, 12, 102, null, 80.0))));
        assertThrows(BusinessException.class, () -> OptimizedAllocationRunService.validateCandidateSet(
                List.of(one, new OptimizedAllocationRunService.Candidate(2, 11, 101, null, 80.0))));
        assertThrows(BusinessException.class, () -> OptimizedAllocationRunService.requireCompleteTeams(
                List.of(
                        new OptimizedAllocationRunService.Candidate(1, 11, 101, 9L, 90.0),
                        new OptimizedAllocationRunService.Candidate(2, 12, 101, 9L, 90.0)),
                java.util.Map.of(9L, 3)));
    }

    @Test
    void localSwapChangesCandidateBedsOnly() {
        var left = new OptimizedAllocationRunService.Candidate(1, 11, 101, null, 90.0);
        var right = new OptimizedAllocationRunService.Candidate(2, 12, 102, null, 80.0);
        var swapped = OptimizedAllocationRunService.swap(left, right);

        org.junit.jupiter.api.Assertions.assertEquals(12, swapped.get(0).bedId());
        org.junit.jupiter.api.Assertions.assertEquals(102, swapped.get(0).roomId());
        org.junit.jupiter.api.Assertions.assertEquals(11, swapped.get(1).bedId());
        org.junit.jupiter.api.Assertions.assertEquals(101, swapped.get(1).roomId());
    }
}
