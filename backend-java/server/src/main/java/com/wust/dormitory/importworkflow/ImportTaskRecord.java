package com.wust.dormitory.importworkflow;

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
        String status,
        List<Map<String, String>> rows,
        List<Map<String, Object>> fieldErrors,
        List<ImportJournalEntry> journal,
        Instant createdAt,
        Instant committedAt,
        Instant rolledBackAt) {

    public ImportTaskRecord {
        rows = rows == null ? List.of() : rows.stream().map(ImportTaskRecord::copyStringMap).toList();
        fieldErrors = fieldErrors == null ? List.of() : fieldErrors.stream().map(ImportTaskRecord::copyObjectMap).toList();
        journal = journal == null ? List.of() : List.copyOf(journal);
    }

    public ImportTaskRecord committed(List<ImportJournalEntry> entries, Instant at) {
        return new ImportTaskRecord(taskId, importType, fileName, digest, idempotencyKey,
                "COMMITTED", rows, fieldErrors, entries, createdAt, at, null);
    }

    public ImportTaskRecord rolledBack(Instant at) {
        return new ImportTaskRecord(taskId, importType, fileName, digest, idempotencyKey,
                "ROLLED_BACK", rows, fieldErrors, journal, createdAt, committedAt, at);
    }

    private static Map<String, String> copyStringMap(Map<String, String> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static Map<String, Object> copyObjectMap(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
