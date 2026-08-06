package com.wust.dormitory.export;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ExportTaskProcessor {
    private static final int MAX_TASKS_PER_TICK = 3;

    private final ExportTaskService taskService;
    private final ExportTaskMapper mapper;
    private final ObjectMapper objectMapper;
    private final Path exportRoot;

    public ExportTaskProcessor(
            ExportTaskService taskService,
            ExportTaskMapper mapper,
            ObjectMapper objectMapper,
            @Value("${wust.dormitory.export-directory:${java.io.tmpdir}/wust-dormitory-exports}")
            String exportDirectory) {
        this.taskService = taskService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.exportRoot = Path.of(exportDirectory).toAbsolutePath().normalize();
    }

    @Scheduled(fixedDelayString = "${wust.dormitory.export-poll-delay-ms:2000}")
    public void processQueuedTasks() {
        for (int index = 0; index < MAX_TASKS_PER_TICK; index++) {
            Optional<Map<String, Object>> claimed = taskService.claimNext();
            if (claimed.isEmpty()) return;
            process(claimed.get());
        }
    }

    void process(Map<String, Object> task) {
        long taskId = ((Number) task.get("id")).longValue();
        try {
            Files.createDirectories(exportRoot);
            taskService.progress(taskId, 10);
            String taskType = String.valueOf(task.get("task_type"));
            if ("null".equals(taskType)) taskType = String.valueOf(task.get("taskType"));
            Map<String, Object> request = request(task.get("request_json") != null
                    ? task.get("request_json") : task.get("requestJson"));
            ExportContent content = switch (taskType) {
                case "AUDIT_EXPORT" -> auditContent(request);
                case "CUSTOM_REPORT" -> reportContent(request);
                default -> throw new IllegalArgumentException("不支持的导出任务类型：" + taskType);
            };
            taskService.progress(taskId, 80);
            Path file = exportRoot.resolve("export-" + taskId + ".csv").normalize();
            if (!file.startsWith(exportRoot)) {
                throw new IllegalStateException("导出文件路径超出允许目录");
            }
            Files.writeString(file, "\uFEFF" + content.csv(), StandardCharsets.UTF_8);
            taskService.succeed(taskId, content.fileName(), Files.size(file), file.toString());
        } catch (Exception exception) {
            taskService.fail(taskId, "EXPORT_PROCESSING_FAILED", safeMessage(exception));
        }
    }

    private ExportContent auditContent(Map<String, Object> request) {
        Map<String, Object> query = map(request.get("query"));
        boolean includeSensitive = Boolean.TRUE.equals(request.get("includeSensitiveData"));
        List<Map<String, Object>> rows = mapper.auditRows(query);
        List<String> columns = new ArrayList<>(List.of(
                "occurredAt", "operatorUserId", "operatorType", "actionType",
                "resourceType", "resourceId", "resultStatus", "reason",
                "requestId", "errorCode", "networkAddress"));
        if (includeSensitive) {
            columns.add("beforeData");
            columns.add("afterData");
        }
        return new ExportContent("audit-export.csv", csv(columns, rows));
    }

    private ExportContent reportContent(Map<String, Object> request) {
        Map<String, Object> filters = map(request.get("filters"));
        List<Map<String, Object>> rows = mapper.reportRows(filters);
        Set<String> columns = new LinkedHashSet<>();
        columns.addAll(strings(request.get("fields")));
        columns.addAll(strings(request.get("metrics")));
        if (columns.isEmpty()) {
            columns.addAll(List.of("batchCode", "batchName", "academicYear", "metricVersion"));
        }
        List<Map<String, Object>> flattened = rows.stream().map(row -> {
            Map<String, Object> result = new LinkedHashMap<>(row);
            result.putAll(map(row.get("metricsJson")));
            result.putAll(map(row.get("dimensionsJson")));
            return result;
        }).toList();
        return new ExportContent(fileName(request.get("name")), csv(List.copyOf(columns), flattened));
    }

    private String csv(List<String> columns, List<Map<String, Object>> rows) {
        StringBuilder csv = new StringBuilder();
        csv.append(columns.stream().map(this::escape).reduce((a, b) -> a + "," + b).orElse(""));
        csv.append('\n');
        for (Map<String, Object> row : rows) {
            for (int index = 0; index < columns.size(); index++) {
                if (index > 0) csv.append(',');
                csv.append(escape(value(row.get(columns.get(index)))));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    private Map<String, Object> request(Object value) throws IOException {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> source) return map(source);
        if (value instanceof byte[] bytes) {
            return objectMapper.readValue(bytes, new TypeReference<>() {});
        }
        return objectMapper.readValue(String.valueOf(value), new TypeReference<>() {});
    }

    private Map<String, Object> map(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        try {
            if (value instanceof JsonNode node) {
                return objectMapper.convertValue(node, new TypeReference<>() {});
            }
            if (value instanceof byte[] bytes) {
                return objectMapper.readValue(bytes, new TypeReference<>() {});
            }
            if (value instanceof String text && !text.isBlank()) {
                return objectMapper.readValue(text, new TypeReference<>() {});
            }
            return objectMapper.convertValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> values)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : values) result.add(String.valueOf(item));
        return result;
    }

    private String value(Object value) {
        if (value == null) return "";
        if (value instanceof TemporalAccessor) return String.valueOf(value);
        if (value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception ignored) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    private String escape(String value) {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.contains(",") || normalized.contains("\n") || normalized.contains("\"")) {
            return "\"" + normalized.replace("\"", "\"\"") + "\"";
        }
        return normalized;
    }

    private String fileName(Object value) {
        String base = value == null ? "custom-report" : String.valueOf(value).trim();
        base = base.replaceAll("[^\\p{L}\\p{N}._-]+", "-");
        if (base.isBlank()) base = "custom-report";
        return base + ".csv";
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() <= 900 ? message : message.substring(0, 900);
    }

    private record ExportContent(String fileName, String csv) {}
}
