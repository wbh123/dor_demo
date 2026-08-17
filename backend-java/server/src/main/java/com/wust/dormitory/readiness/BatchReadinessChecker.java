package com.wust.dormitory.readiness;

import com.wust.dormitory.admin.BatchRuleTemplateService;
import com.wust.dormitory.admin.BatchScopeService;
import com.wust.dormitory.matching.MatchingSchemeService;
import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import com.wust.dormitory.residency.BatchRoomLockService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class BatchReadinessChecker implements ReadinessChecker {
    private final SystemReadinessMapper mapper;
    private final BatchScopeService batchScopeService;
    private final BatchRuleTemplateService batchRuleTemplateService;
    private final BatchRoomLockService batchRoomLockService;
    private final MatchingSchemeService matchingSchemeService;

    public BatchReadinessChecker(
            SystemReadinessMapper mapper,
            BatchScopeService batchScopeService,
            BatchRuleTemplateService batchRuleTemplateService,
            BatchRoomLockService batchRoomLockService,
            MatchingSchemeService matchingSchemeService) {
        this.mapper = mapper;
        this.batchScopeService = batchScopeService;
        this.batchRuleTemplateService = batchRuleTemplateService;
        this.batchRoomLockService = batchRoomLockService;
        this.matchingSchemeService = matchingSchemeService;
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
            return List.of(ReadinessCheckResult.info(
                    "BATCH_NONE_ACTIVE",
                    category(),
                    "当前选寝批次",
                    "当前没有 PUBLISHED、OPEN 或 PAUSED 批次；这不阻断系统基础上线。",
                    context.checkedAt()));
        }

        List<ReadinessCheckResult> results = new ArrayList<>();
        for (Map<String, Object> batch : batches) {
            results.add(checkBatch(batch, context));
        }
        return results;
    }

    private ReadinessCheckResult checkBatch(Map<String, Object> batch, ReadinessContext context) {
        long batchId = ((Number) batch.get("id")).longValue();
        String name = String.valueOf(batch.get("batchName"));
        String status = String.valueOf(batch.get("batchStatus"));
        Long ruleTemplateId = nullableLong(batch.get("ruleTemplateId"));
        long participantCount = mapper.participantCount(batchId);
        long pendingParticipantCount = mapper.pendingParticipantCount(batchId);

        CheckAttempt scopeAttempt = attempt(() -> batchScopeService.requireReady(batchId));
        CheckAttempt ruleAttempt = ruleTemplateId == null
                ? CheckAttempt.failed("RULE_TEMPLATE_NOT_BOUND")
                : attempt(() -> batchRuleTemplateService.resolveForBatch(ruleTemplateId));
        CheckAttempt matchingAttempt = attempt(() -> matchingSchemeService.policyForBatch(batchId));

        Map<String, Object> preview = Map.of();
        CheckAttempt preflightAttempt;
        try {
            preview = batchRoomLockService.preview(batchId);
            preflightAttempt = CheckAttempt.passed();
        } catch (RuntimeException exception) {
            preflightAttempt = CheckAttempt.failed(exception);
        }

        long roomCount = number(preview.get("roomCount"));
        long capacity = number(preview.get("availableCapacity"));
        long studentConflictCount = number(preview.get("studentConflictCount"));
        boolean publishable = preflightAttempt.success() && Boolean.TRUE.equals(preview.get("publishable"));
        boolean capacityShortage = pendingParticipantCount > capacity;
        boolean blocked = !scopeAttempt.success()
                || !ruleAttempt.success()
                || !matchingAttempt.success()
                || !publishable
                || capacityShortage;

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("batchId", batchId);
        evidence.put("batchName", name);
        evidence.put("batchStatus", status);
        evidence.put("participantCount", participantCount);
        evidence.put("pendingParticipantCount", pendingParticipantCount);
        evidence.put("openRoomCount", roomCount);
        evidence.put("availableCapacity", capacity);
        evidence.put("capacityShortage", capacityShortage);
        evidence.put("studentConflictCount", studentConflictCount);
        evidence.put("scopeReady", scopeAttempt.success());
        evidence.put("ruleRevisionBound", ruleTemplateId != null);
        evidence.put("ruleRevisionValid", ruleAttempt.success());
        if (ruleTemplateId != null) {
            evidence.put("ruleTemplateId", ruleTemplateId);
        }
        evidence.put("matchingSchemeValid", matchingAttempt.success());
        evidence.put("publishable", publishable);
        putFailure(evidence, "scopeCheckError", scopeAttempt);
        putFailure(evidence, "ruleCheckError", ruleAttempt);
        putFailure(evidence, "matchingCheckError", matchingAttempt);
        putFailure(evidence, "publishPrecheckError", preflightAttempt);

        return ReadinessCheckResult.of(
                "BATCH_" + batchId + "_PREFLIGHT",
                category(),
                "批次发布预检 · " + name,
                blocked ? ReadinessSeverity.ERROR : ReadinessSeverity.PASS,
                blocked,
                blocked ? "FAILED" : "PASSED",
                blocked ? "批次“" + name + "”当前不满足开放条件。" : "批次“" + name + "”通过只读发布预检。",
                evidence,
                blocked ? "前往批次管理修正范围、规则修订、容量、匹配方案或冲突后重新检查" : null,
                "/admin/batches",
                context.checkedAt());
    }

    private CheckAttempt attempt(CheckedAction action) {
        try {
            action.run();
            return CheckAttempt.passed();
        } catch (RuntimeException exception) {
            return CheckAttempt.failed(exception);
        }
    }

    private void putFailure(Map<String, Object> evidence, String key, CheckAttempt attempt) {
        if (!attempt.success() && attempt.errorType() != null) {
            evidence.put(key, attempt.errorType());
        }
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Long nullableLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run();
    }

    private record CheckAttempt(boolean success, String errorType) {
        private static CheckAttempt passed() {
            return new CheckAttempt(true, null);
        }

        private static CheckAttempt failed(RuntimeException exception) {
            return failed(exception.getClass().getSimpleName());
        }

        private static CheckAttempt failed(String errorType) {
            return new CheckAttempt(false, errorType);
        }
    }
}
