from pathlib import Path

ROOT = Path('private-repo')


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding='utf-8')


def write(path: str, text: str) -> None:
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding='utf-8')


def replace(path: str, old: str, new: str, count: int = 1) -> None:
    text = read(path)
    if old not in text:
        if new in text:
            return
        raise SystemExit(f'missing replacement marker in {path}: {old[:160]!r}')
    write(path, text.replace(old, new, count))

# Frontend type-check fixes.
p = 'frontend/src/account/useAccountAdminConsole.ts'
replace(p, "import type { DataObject } from '../api/types'\nimport { useAccountAdminSession } from './session'",
           "import type { DataObject } from '../api/types'\nimport { useAccountAdminSession } from './session'\nimport type { ScopeType } from './scopeSelection'")
replace(p, "  scopeType: 'BUILDING',", "  scopeType: 'BUILDING' as ScopeType,")
replace(p, "  scopeType: 'SCHOOL',", "  scopeType: 'SCHOOL' as ScopeType,")

p = 'frontend/src/components/admin/ImportWorkflowModal.vue'
replace(p, "import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'",
           "import { computed, onBeforeUnmount, ref, watch } from 'vue'")
replace(p, '''    const task = (response.data.data ?? {}) as DataObject
    selected.value = task
    message.value = '文件已上传，后台预检任务已创建。可以关闭窗口，任务会继续执行。' ''',
'''    const task = (response.data.data ?? {}) as DataObject
    selected.value = task
    attemptId.value = ''
    message.value = '文件已上传，后台预检任务已创建。可以关闭窗口，任务会继续执行。' ''')

p = 'frontend/src/views/admin/AdminImportQualityView.vue'
replace(p, '''    const task = (response.data.data ?? {}) as DataObject
    selected.value = task
    upsertTaskSummary(task)''',
'''    const task = (response.data.data ?? {}) as DataObject
    selected.value = task
    uploadAttemptId.value = ''
    upsertTaskSummary(task)''')

# Durable import-policy snapshot on every task.
write('backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportTaskRecord.java', '''package com.wust.dormitory.importworkflow;

import com.wust.dormitory.security.CurrentUser;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ImportTaskRecord(
        String taskId,
        String importType,
        String fileName,
        String digest,
        String idempotencyKey,
        Map<String, Object> policySnapshot,
        String status,
        List<Map<String, String>> rows,
        List<Map<String, Object>> fieldErrors,
        List<ImportJournalEntry> journal,
        int totalRows,
        int processedRows,
        int invalidRows,
        int mutationCount,
        String currentStage,
        String failureCode,
        String failureMessage,
        String workerId,
        Instant leaseUntil,
        Long requestedByUserId,
        String requestedByUsername,
        String requestedByDisplayName,
        String requestedByUserType,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        Instant committedAt,
        Instant rolledBackAt) {

    public ImportTaskRecord {
        policySnapshot = policySnapshot == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(policySnapshot));
        rows = rows == null ? List.of() : rows.stream().map(ImportTaskRecord::copyStringMap).toList();
        fieldErrors = fieldErrors == null ? List.of() : fieldErrors.stream().map(ImportTaskRecord::copyObjectMap).toList();
        journal = journal == null ? List.of() : List.copyOf(journal);
        totalRows = Math.max(totalRows, 0);
        processedRows = Math.max(0, Math.min(processedRows, totalRows));
        invalidRows = Math.max(0, Math.min(invalidRows, totalRows));
        mutationCount = Math.max(mutationCount, 0);
    }

    /** Compatibility constructor for callers created before policy snapshots were persisted. */
    public ImportTaskRecord(
            String taskId,
            String importType,
            String fileName,
            String digest,
            String idempotencyKey,
            String status,
            List<Map<String, String>> rows,
            List<Map<String, Object>> fieldErrors,
            List<ImportJournalEntry> journal,
            int totalRows,
            int processedRows,
            int invalidRows,
            int mutationCount,
            String currentStage,
            String failureCode,
            String failureMessage,
            String workerId,
            Instant leaseUntil,
            Long requestedByUserId,
            String requestedByUsername,
            String requestedByDisplayName,
            String requestedByUserType,
            Instant createdAt,
            Instant startedAt,
            Instant finishedAt,
            Instant committedAt,
            Instant rolledBackAt) {
        this(taskId, importType, fileName, digest, idempotencyKey, Map.of(), status,
                rows, fieldErrors, journal, totalRows, processedRows, invalidRows, mutationCount,
                currentStage, failureCode, failureMessage, workerId, leaseUntil,
                requestedByUserId, requestedByUsername, requestedByDisplayName, requestedByUserType,
                createdAt, startedAt, finishedAt, committedAt, rolledBackAt);
    }

    /** Backward-compatible constructor for existing tests and durable V22 tasks. */
    public ImportTaskRecord(
            String taskId,
            String importType,
            String fileName,
            String digest,
            String idempotencyKey,
            String status,
            List<Map<String, String>> rows,
            List<Map<String, Object>> fieldErrors,
            List<ImportJournalEntry> journal,
            Instant createdAt,
            Instant committedAt,
            Instant rolledBackAt) {
        this(
                taskId, importType, fileName, digest, idempotencyKey, Map.of(), status,
                rows, fieldErrors, journal,
                rows == null ? 0 : rows.size(),
                rows == null ? 0 : rows.size(),
                invalidRowCount(fieldErrors),
                journal == null ? 0 : journal.size(),
                legacyStage(status), null, null, null, null,
                null, null, null, null,
                createdAt, null, terminal(status) ? terminalTime(status, committedAt, rolledBackAt) : null,
                committedAt, rolledBackAt);
    }

    public static ImportTaskRecord previewQueued(
            String taskId,
            String importType,
            String fileName,
            String digest,
            String idempotencyKey,
            Instant createdAt) {
        return previewQueued(taskId, importType, fileName, digest, idempotencyKey, Map.of(), createdAt);
    }

    public static ImportTaskRecord previewQueued(
            String taskId,
            String importType,
            String fileName,
            String digest,
            String idempotencyKey,
            Map<String, Object> policySnapshot,
            Instant createdAt) {
        return new ImportTaskRecord(
                taskId, importType, fileName, digest, idempotencyKey, policySnapshot,
                "PREVIEW_QUEUED", List.of(), List.of(), List.of(),
                0, 0, 0, 0,
                "等待预检", null, null, null, null,
                null, null, null, null,
                createdAt, null, null, null, null);
    }

    public ImportTaskRecord running(String nextStatus, String nextWorkerId, Instant nextLeaseUntil, Instant at) {
        return new ImportTaskRecord(
                taskId, importType, fileName, digest, idempotencyKey, policySnapshot, nextStatus,
                rows, fieldErrors, journal,
                totalRows, processedRows, invalidRows, mutationCount,
                runningStage(nextStatus), null, null, nextWorkerId, nextLeaseUntil,
                requestedByUserId, requestedByUsername, requestedByDisplayName, requestedByUserType,
                createdAt, at, null, committedAt, rolledBackAt);
    }

    public ImportTaskRecord previewed(
            List<Map<String, String>> parsedRows,
            List<Map<String, Object>> errors,
            Instant at) {
        int count = parsedRows == null ? 0 : parsedRows.size();
        return new ImportTaskRecord(
                taskId, importType, fileName, digest, idempotencyKey, policySnapshot, "PREVIEWED",
                parsedRows, errors, List.of(),
                count, count, invalidRowCount(errors), 0,
                "预检完成", null, null, null, null,
                requestedByUserId, requestedByUsername, requestedByDisplayName, requestedByUserType,
                createdAt, startedAt, at, null, null);
    }

    public ImportTaskRecord queueCommit(CurrentUser operator) {
        return queuedWithOperator("COMMIT_QUEUED", "等待正式导入", operator);
    }

    public ImportTaskRecord queueRollback(CurrentUser operator) {
        return queuedWithOperator("ROLLBACK_QUEUED", "等待回滚", operator);
    }

    public ImportTaskRecord committed(List<ImportJournalEntry> entries, Instant at) {
        return new ImportTaskRecord(
                taskId, importType, fileName, digest, idempotencyKey, policySnapshot,
                "COMMITTED", rows, fieldErrors, entries,
                totalRows, totalRows, invalidRows, entries == null ? 0 : entries.size(),
                "导入完成", null, null, null, null,
                requestedByUserId, requestedByUsername, requestedByDisplayName, requestedByUserType,
                createdAt, startedAt, at, at, null);
    }

    public ImportTaskRecord rolledBack(Instant at) {
        return new ImportTaskRecord(
                taskId, importType, fileName, digest, idempotencyKey, policySnapshot,
                "ROLLED_BACK", rows, fieldErrors, journal,
                totalRows, totalRows, invalidRows, mutationCount,
                "回滚完成", null, null, null, null,
                requestedByUserId, requestedByUsername, requestedByDisplayName, requestedByUserType,
                createdAt, startedAt, at, committedAt, at);
    }

    public ImportTaskRecord failed(String failedStatus, String code, String message, Instant at) {
        return new ImportTaskRecord(
                taskId, importType, fileName, digest, idempotencyKey, policySnapshot, failedStatus,
                rows, fieldErrors, journal,
                totalRows, processedRows, invalidRows, mutationCount,
                "任务失败", code, message, null, null,
                requestedByUserId, requestedByUsername, requestedByDisplayName, requestedByUserType,
                createdAt, startedAt, at, committedAt, rolledBackAt);
    }

    public ImportTaskRecord withProgress(int processed, String stage, String activeWorkerId, Instant activeLeaseUntil) {
        return new ImportTaskRecord(
                taskId, importType, fileName, digest, idempotencyKey, policySnapshot, status,
                rows, fieldErrors, journal,
                totalRows, processed, invalidRows, mutationCount,
                stage, failureCode, failureMessage, activeWorkerId, activeLeaseUntil,
                requestedByUserId, requestedByUsername, requestedByDisplayName, requestedByUserType,
                createdAt, startedAt, finishedAt, committedAt, rolledBackAt);
    }

    public CurrentUser operator() {
        if (requestedByUserId == null) return null;
        return new CurrentUser(
                requestedByUserId,
                null,
                requestedByUsername == null ? "" : requestedByUsername,
                requestedByDisplayName == null ? "" : requestedByDisplayName,
                requestedByUserType == null ? "ADMIN" : requestedByUserType);
    }

    public boolean isTerminal() {
        return terminal(status);
    }

    private ImportTaskRecord queuedWithOperator(String nextStatus, String stage, CurrentUser operator) {
        return new ImportTaskRecord(
                taskId, importType, fileName, digest, idempotencyKey, policySnapshot, nextStatus,
                rows, fieldErrors, journal,
                totalRows, 0, invalidRows, mutationCount,
                stage, null, null, null, null,
                operator == null ? null : operator.userId(),
                operator == null ? null : operator.username(),
                operator == null ? null : operator.displayName(),
                operator == null ? null : operator.userType(),
                createdAt, null, null, committedAt, rolledBackAt);
    }

    private static int invalidRowCount(List<Map<String, Object>> errors) {
        if (errors == null || errors.isEmpty()) return 0;
        return (int) errors.stream()
                .map(error -> error.get("row"))
                .filter(value -> value != null)
                .distinct()
                .count();
    }

    private static boolean terminal(String status) {
        return List.of(
                "PREVIEWED", "PREVIEW_FAILED", "COMMITTED", "COMMIT_FAILED",
                "ROLLED_BACK", "ROLLBACK_FAILED").contains(status);
    }

    private static Instant terminalTime(String status, Instant committedAt, Instant rolledBackAt) {
        if ("ROLLED_BACK".equals(status)) return rolledBackAt;
        if ("COMMITTED".equals(status)) return committedAt;
        return null;
    }

    private static String legacyStage(String status) {
        return switch (status) {
            case "PREVIEWED" -> "预检完成";
            case "COMMITTED" -> "导入完成";
            case "ROLLED_BACK" -> "回滚完成";
            default -> null;
        };
    }

    private static String runningStage(String status) {
        return switch (status) {
            case "PREVIEWING" -> "解析并校验导入文件";
            case "COMMITTING" -> "写入业务数据";
            case "ROLLING_BACK" -> "安全回滚业务数据";
            default -> status;
        };
    }

    private static Map<String, String> copyStringMap(Map<String, String> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<String, Object> copyObjectMap(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
''')

p = 'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/ImportWorkflowService.java'
replace(p, '''                    digest,
                    normalizedKey,
                    Instant.now());''',
'''                    digest,
                    normalizedKey,
                    policySnapshotFor(normalizedType),
                    Instant.now());''', count=2)
replace(p, '''        result.put("idempotencyKey", task.idempotencyKey());
        result.put("status", task.status());''',
'''        result.put("idempotencyKey", task.idempotencyKey());
        result.put("policySnapshot", task.policySnapshot());
        result.put("status", task.status());''')
replace(p, '''    private String normalizeIdempotencyKey(String supplied, String type, String digest) {
        String trimmed = supplied == null ? "" : supplied.trim();
        if (!trimmed.isBlank()) return trimmed;
        return "attempt:" + type + ':' + UUID.randomUUID();
    }
''',
'''    private String normalizeIdempotencyKey(String supplied, String type, String digest) {
        String trimmed = supplied == null ? "" : supplied.trim();
        if (!trimmed.isBlank()) return trimmed;
        return "attempt:" + type + ':' + UUID.randomUUID();
    }

    private Map<String, Object> policySnapshotFor(String type) {
        if (!"ROOM".equals(type)) return Map.of();
        return Map.of("roomAutoCreateBuilding", importPolicyService.roomAutoCreateBuildingEnabled());
    }
''')

p = 'backend-java/server/src/main/java/com/wust/dormitory/importworkflow/JdbcImportTaskRepository.java'
replace(p, '''                string(header.get("idempotencyKey")),
                string(header.get("status")),''',
'''                string(header.get("idempotencyKey")),
                policySnapshot(header.get("policySnapshot")),
                string(header.get("status")),''')
replace(p, '''        params.put("idempotencyKey", task.idempotencyKey());
        params.put("status", task.status());''',
'''        params.put("idempotencyKey", task.idempotencyKey());
        params.put("policySnapshotJson", json(task.policySnapshot()));
        params.put("status", task.status());''')
replace(p, '''    private int invalidRowCount(List<Map<String, Object>> errors) {''',
'''    private Map<String, Object> policySnapshot(Object value) {
        if (value == null) return Map.of();
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? Map.of() : read(text, OBJECT_MAP);
    }

    private int invalidRowCount(List<Map<String, Object>> errors) {''')

p = 'backend-java/server/src/main/resources/mapper/importworkflow/ImportTaskPersistenceMapper.xml'
replace(p, '''        idempotency_key AS idempotencyKey,
        task_status AS status,''',
'''        idempotency_key AS idempotencyKey,
        policy_snapshot AS policySnapshot,
        task_status AS status,''')
replace(p, '''        (task_id, import_type, file_name, file_digest, idempotency_key,
         task_status,''',
'''        (task_id, import_type, file_name, file_digest, idempotency_key, policy_snapshot,
         task_status,''')
replace(p, '''        (#{task.taskId}, #{task.importType}, #{task.fileName}, #{task.digest}, #{task.idempotencyKey},
         #{task.status},''',
'''        (#{task.taskId}, #{task.importType}, #{task.fileName}, #{task.digest}, #{task.idempotencyKey}, CAST(#{task.policySnapshotJson} AS JSON),
         #{task.status},''')
replace(p, '''            idempotency_key=VALUES(idempotency_key),
            task_status=VALUES(task_status),''',
'''            idempotency_key=VALUES(idempotency_key),
            policy_snapshot=VALUES(policy_snapshot),
            task_status=VALUES(task_status),''')

migration = ROOT / 'backend-java/server/src/main/resources/db/migration/V70__add_import_policy_snapshot.sql'
if not migration.exists():
    migration.write_text('''-- V70: persist the policy used when an import task is created.\n-- Existing tasks predate policy snapshots and are backfilled with an empty object.\n\nALTER TABLE import_task\n    ADD COLUMN policy_snapshot JSON NULL COMMENT '创建任务时的导入策略快照' AFTER idempotency_key;\n\nUPDATE import_task\nSET policy_snapshot = JSON_OBJECT()\nWHERE policy_snapshot IS NULL;\n\nALTER TABLE import_task\n    MODIFY COLUMN policy_snapshot JSON NOT NULL COMMENT '创建任务时的导入策略快照';\n''', encoding='utf-8')

print('runtime follow-up fixes applied')
