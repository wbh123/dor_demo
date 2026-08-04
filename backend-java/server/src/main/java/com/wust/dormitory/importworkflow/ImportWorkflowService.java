package com.wust.dormitory.importworkflow;

import com.wust.dormitory.common.error.BusinessException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ImportWorkflowService {
    private static final String DIGEST_ALGORITHM = "SHA-256";
    private static final Map<String, List<String>> REQUIRED_HEADERS = Map.of(
            "STUDENT", List.of("studentNumber", "name", "gender", "majorCode"),
            "ROOM", List.of("buildingCode", "floorNumber", "roomNumber", "capacity")
    );

    private final Map<String, ImportTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> idempotencyIndex = new ConcurrentHashMap<>();

    public Map<String, Object> preview(MultipartFile file, String importType, String idempotencyKey) {
        String normalizedType = normalizeType(importType);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("IMPORT_FILE_REQUIRED", "请选择需要预检的导入文件");
        }
        try {
            byte[] bytes = file.getBytes();
            String digest = digest(bytes);
            String normalizedKey = normalizeIdempotencyKey(idempotencyKey, normalizedType, digest);
            String existingTaskId = idempotencyIndex.get(normalizedKey);
            if (existingTaskId != null) {
                ImportTask existing = tasks.get(existingTaskId);
                if (existing != null && existing.digest().equals(digest)) {
                    return existing.toMap();
                }
                throw new BusinessException(
                        "IDEMPOTENCY_CONFLICT",
                        "该幂等键已经用于其他导入文件",
                        HttpStatus.CONFLICT);
            }

            ParsedPreview parsed = parse(file.getOriginalFilename(), bytes, normalizedType);
            String taskId = UUID.randomUUID().toString();
            ImportTask task = new ImportTask(
                    taskId,
                    normalizedType,
                    file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename(),
                    digest,
                    normalizedKey,
                    "PREVIEWED",
                    parsed.totalRows(),
                    parsed.validRows(),
                    parsed.fieldErrors(),
                    Instant.now(),
                    null,
                    null);
            tasks.put(taskId, task);
            idempotencyIndex.put(normalizedKey, taskId);
            return task.toMap();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("IMPORT_PREVIEW_FAILED", "导入文件预检失败：" + exception.getMessage());
        }
    }

    public List<Map<String, Object>> listTasks() {
        return tasks.values().stream()
                .sorted((left, right) -> right.createdAt().compareTo(left.createdAt()))
                .map(ImportTask::toMap)
                .toList();
    }

    public Map<String, Object> getTask(String taskId) {
        return requireTask(taskId).toMap();
    }

    public synchronized Map<String, Object> commitTask(String taskId) {
        ImportTask task = requireTask(taskId);
        if ("COMMITTED".equals(task.status())) {
            return task.toMap();
        }
        if (!"PREVIEWED".equals(task.status())) {
            throw new BusinessException("IMPORT_TASK_STATE_INVALID", "只有预检完成的任务可以提交", HttpStatus.CONFLICT);
        }
        if (!task.fieldErrors().isEmpty()) {
            throw new BusinessException("IMPORT_VALIDATION_FAILED", "预检仍存在字段错误，不能提交", HttpStatus.CONFLICT);
        }
        ImportTask committed = task.withStatus("COMMITTED", Instant.now(), null);
        tasks.put(taskId, committed);
        return committed.toMap();
    }

    public synchronized Map<String, Object> rollbackTask(String taskId) {
        ImportTask task = requireTask(taskId);
        if ("ROLLED_BACK".equals(task.status())) {
            return task.toMap();
        }
        if (!"COMMITTED".equals(task.status())) {
            throw new BusinessException("IMPORT_TASK_STATE_INVALID", "只有已经提交的任务可以回滚", HttpStatus.CONFLICT);
        }
        ImportTask rolledBack = task.withStatus("ROLLED_BACK", task.committedAt(), Instant.now());
        tasks.put(taskId, rolledBack);
        return rolledBack.toMap();
    }

    public byte[] errorsCsv(String taskId) {
        ImportTask task = requireTask(taskId);
        StringBuilder csv = new StringBuilder("row,field,value,message\n");
        for (Map<String, Object> error : task.fieldErrors()) {
            csv.append(csvValue(error.get("row"))).append(',')
                    .append(csvValue(error.get("field"))).append(',')
                    .append(csvValue(error.get("value"))).append(',')
                    .append(csvValue(error.get("message"))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private ParsedPreview parse(String filename, byte[] bytes, String type) throws Exception {
        String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls")
                ? parseWorkbook(bytes, type)
                : parseCsv(bytes, type);
    }

    private ParsedPreview parseCsv(byte[] bytes, String type) {
        List<String> lines = new String(bytes, StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.isEmpty()) {
            return new ParsedPreview(0, 0, List.of(error(1, "file", "", "文件没有可读取的数据")));
        }
        String[] headers = lines.getFirst().split(",", -1);
        List<Map<String, Object>> errors = validateHeaders(List.of(headers), type);
        int total = Math.max(0, lines.size() - 1);
        for (int index = 1; index < lines.size(); index++) {
            String[] values = lines.get(index).split(",", -1);
            validateRequiredValues(index + 1, List.of(headers), List.of(values), type, errors);
        }
        int invalidRows = (int) errors.stream().map(item -> item.get("row")).distinct().count();
        return new ParsedPreview(total, Math.max(0, total - invalidRows), List.copyOf(errors));
    }

    private ParsedPreview parseWorkbook(byte[] bytes, String type) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                return new ParsedPreview(0, 0, List.of(error(1, "file", "", "工作表没有表头")));
            }
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < headerRow.getLastCellNum(); column++) {
                headers.add(formatter.formatCellValue(headerRow.getCell(column)).trim());
            }
            List<Map<String, Object>> errors = validateHeaders(headers, type);
            int total = 0;
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                boolean anyValue = false;
                for (int column = 0; column < headers.size(); column++) {
                    String value = formatter.formatCellValue(row.getCell(column)).trim();
                    values.add(value);
                    anyValue = anyValue || !value.isBlank();
                }
                if (!anyValue) {
                    continue;
                }
                total++;
                validateRequiredValues(rowIndex + 1, headers, values, type, errors);
            }
            int invalidRows = (int) errors.stream().map(item -> item.get("row")).distinct().count();
            return new ParsedPreview(total, Math.max(0, total - invalidRows), List.copyOf(errors));
        }
    }

    private List<Map<String, Object>> validateHeaders(List<String> headers, String type) {
        List<Map<String, Object>> errors = new ArrayList<>();
        for (String required : REQUIRED_HEADERS.get(type)) {
            if (!headers.contains(required)) {
                errors.add(error(1, required, "", "缺少必填表头"));
            }
        }
        return errors;
    }

    private void validateRequiredValues(
            int rowNumber,
            List<String> headers,
            List<String> values,
            String type,
            List<Map<String, Object>> errors) {
        for (String required : REQUIRED_HEADERS.get(type)) {
            int index = headers.indexOf(required);
            if (index < 0) {
                continue;
            }
            String value = index < values.size() ? values.get(index).trim() : "";
            if (value.isBlank()) {
                errors.add(error(rowNumber, required, value, "必填字段不能为空"));
            }
        }
    }

    private ImportTask requireTask(String taskId) {
        ImportTask task = tasks.get(taskId);
        if (task == null) {
            throw new BusinessException("IMPORT_TASK_NOT_FOUND", "导入任务不存在", HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private String normalizeType(String value) {
        String normalized = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (!REQUIRED_HEADERS.containsKey(normalized)) {
            throw new BusinessException("IMPORT_TYPE_INVALID", "导入类型只支持 STUDENT 或 ROOM");
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(String supplied, String type, String digest) {
        String trimmed = supplied == null ? "" : supplied.trim();
        return trimmed.isBlank() ? type + ":" + digest : trimmed;
    }

    private String digest(byte[] bytes) throws Exception {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance(DIGEST_ALGORITHM).digest(bytes));
    }

    private Map<String, Object> error(int row, String field, String value, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("row", row);
        result.put("field", field);
        result.put("value", value);
        result.put("message", message);
        return result;
    }

    private String csvValue(Object value) {
        String text = String.valueOf(value == null ? "" : value).replace("\"", "\"\"");
        return '"' + text + '"';
    }

    private record ParsedPreview(int totalRows, int validRows, List<Map<String, Object>> fieldErrors) {
    }

    private record ImportTask(
            String taskId,
            String importType,
            String fileName,
            String digest,
            String idempotencyKey,
            String status,
            int totalRows,
            int validRows,
            List<Map<String, Object>> fieldErrors,
            Instant createdAt,
            Instant committedAt,
            Instant rolledBackAt) {

        ImportTask withStatus(String nextStatus, Instant nextCommittedAt, Instant nextRolledBackAt) {
            return new ImportTask(
                    taskId,
                    importType,
                    fileName,
                    digest,
                    idempotencyKey,
                    nextStatus,
                    totalRows,
                    validRows,
                    fieldErrors,
                    createdAt,
                    nextCommittedAt,
                    nextRolledBackAt);
        }

        Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("taskId", taskId);
            result.put("importType", importType);
            result.put("fileName", fileName);
            result.put("digestAlgorithm", DIGEST_ALGORITHM);
            result.put("digest", digest);
            result.put("idempotencyKey", idempotencyKey);
            result.put("status", status);
            result.put("totalRows", totalRows);
            result.put("validRows", validRows);
            result.put("invalidRows", Math.max(0, totalRows - validRows));
            result.put("fieldErrors", fieldErrors);
            result.put("createdAt", createdAt.toString());
            result.put("committedAt", committedAt == null ? null : committedAt.toString());
            result.put("rolledBackAt", rolledBackAt == null ? null : rolledBackAt.toString());
            return result;
        }
    }
}
