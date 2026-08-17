package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import com.wust.dormitory.residency.BatchRoomLockService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class BatchReadinessChecker implements ReadinessChecker {
    private final SystemReadinessMapper mapper;
    private final BatchRoomLockService batchRoomLockService;

    public BatchReadinessChecker(SystemReadinessMapper mapper, BatchRoomLockService batchRoomLockService) {
        this.mapper = mapper;
        this.batchRoomLockService = batchRoomLockService;
    }

    @Override
    public String category() {
        return "BATCH";
    }

    @Override
    public boolean critical() {
        return true;
    }

    @Override
    public List<ReadinessCheckResult> check(ReadinessContext context) {
        List<Map<String, Object>> batches = mapper.activeBatches();
        if (batches.isEmpty()) {
            return List.of(ReadinessCheckResult.info("BATCH_NONE_ACTIVE", category(), "当前选寝批次",
                    "当前没有 PUBLISHED、OPEN 或 PAUSED 批次；这不阻断系统基础上线。", context.checkedAt()));
        }
        List<ReadinessCheckResult> results = new ArrayList<>();
        for (Map<String, Object> batch : batches) {
            long batchId = ((Number) batch.get("id")).longValue();
            String name = String.valueOf(batch.get("batchName"));
            String status = String.valueOf(batch.get("batchStatus"));
            long participantCount = mapper.participantCount(batchId);
            Map<String, Object> preview = batchRoomLockService.preview(batchId);
            long roomCount = number(preview.get("roomCount"));
            long capacity = number(preview.get("availableCapacity"));
            boolean publishable = Boolean.TRUE.equals(preview.get("publishable"));
            boolean capacityShortage = participantCount > capacity;
            boolean blocked = !publishable || capacityShortage;
            results.add(ReadinessCheckResult.of(
                    "BATCH_" + batchId + "_PREFLIGHT", category(), "批次发布预检 · " + name,
                    blocked ? ReadinessSeverity.ERROR : ReadinessSeverity.PASS,
                    blocked, blocked ? "FAILED" : "PASSED",
                    blocked ? "批次“" + name + "”当前不满足开放条件。" : "批次“" + name + "”通过只读发布预检。",
                    Map.of("batchId", batchId, "batchName", name, "batchStatus", status,
                            "participantCount", participantCount, "openRoomCount", roomCount,
                            "availableCapacity", capacity, "capacityShortage", capacityShortage,
                            "publishable", publishable,
                            "studentConflictCount", number(preview.get("studentConflictCount"))),
                    blocked ? "前往批次管理修正范围、容量或冲突后重新检查" : null,
                    "/admin/batches", context.checkedAt()));
        }
        return results;
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
