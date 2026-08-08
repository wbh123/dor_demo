package com.wust.dormitory.allocation;

import com.wust.dormitory.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AdminAllocationService {
    private final AllocationSnapshotReader snapshotReader;
    private final AllocationCommitService commitService;
    private final BaselineAllocationPlanner planner = new BaselineAllocationPlanner();

    public AdminAllocationService(
            AllocationSnapshotReader snapshotReader,
            AllocationCommitService commitService) {
        this.snapshotReader = snapshotReader;
        this.commitService = commitService;
    }

    public Map<String, Object> preview(long batchId, long randomSeed) {
        return planner.plan(snapshotReader.read(batchId), randomSeed).toMap();
    }

    public Map<String, Object> commit(
            long batchId,
            long randomSeed,
            String idempotencyKey,
            CurrentUser operator) {
        return commitService.commit(batchId, randomSeed, idempotencyKey, operator);
    }
}
