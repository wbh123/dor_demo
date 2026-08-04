package com.wust.dormitory.importworkflow;

import java.util.LinkedHashMap;
import java.util.Map;

public record ImportJournalEntry(
        String action,
        long entityId,
        Map<String, Object> beforeState,
        Map<String, Object> afterState,
        Map<String, Object> metadata) {

    public ImportJournalEntry {
        beforeState = beforeState == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(beforeState));
        afterState = afterState == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(afterState));
        metadata = metadata == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metadata));
    }
}
