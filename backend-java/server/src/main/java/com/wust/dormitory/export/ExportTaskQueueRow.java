package com.wust.dormitory.export;

public record ExportTaskQueueRow(
        Long id,
        String taskType,
        String requestJson,
        String downloadToken) {
}
