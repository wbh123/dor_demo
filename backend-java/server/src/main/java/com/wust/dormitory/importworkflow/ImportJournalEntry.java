package com.wust.dormitory.importworkflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ImportJournalEntry(
        String action,
        long entityId,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        Map<String, Object> metadata) {

    public ImportJournalEntry {
        beforeState = immutableCopy(beforeState);
        afterState = immutableCopy(afterState);
        metadata = immutableCopy(metadata);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source == null ? Map.of() : source));
    }
}
