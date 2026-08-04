package com.wust.dormitory.importworkflow;

import com.wust.dormitory.admin.SpreadsheetSupport;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ImportWorkflowService {
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final ImportTaskRepository repository;
    private final ImportMutationService mutationService;

    public ImportWorkflowService(
            ImportTaskRepository repository,
            ImportMutationService mutationService) {
        this.repository = repository;
        this.mutationService = mutationService;
    }

    public Map<String, Object> preview(MultipartFile file, String importType, String idempotencyKey) {
        String normalizedType = normalizeType(importType);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("IMPORT_FILE_REQUIRED", "请选择需要预检的导入文件");
        }
        try {
            byte[] bytes = file.getBytes();
            String digest = digest(bytes);
            String normalizedKey = normalizeIdempotencyKey(idempotencyKey, normalizedType, digest);
            ImportTaskRecord existing = repository.findByIdempotencyKey(normalizedKey).orElse(null);
            if (existing != null) {
                if (existing.digest().equals(digest) && existing.importType().equals(normalizedType)) {
                    return toMap(existing);
                }
                throw new BusinessException(
                        "IDEMPOTENCY_CONFLICT",
                        "该幂等键已经用于其他导入文件",
                        HttpStatus.CONFLICT);
            }

            List<Map<String, String>> rows = SpreadsheetSupport.read(file);
            if (rows.isEmpty()) {
                throw new BusinessException("IMPORT_EMPTY", "文件中没有可预检的数据");
            }
            List<Map<String, Object>> fieldErrors = validateRows(normalizedType, rows);
            ImportTaskRecord task = new ImportTaskRecord(
                    UUID.randomUUID().toString(),
                    normalizedType,
                    file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename(),
                    digest,
                    normalizedKey,
                    "PREVIEWED",
                    rows,
                    fieldErrors,
                    List.of(),
                    Instant.now(),
                    null,
                    null);
            repository.save(task);
            return toMap(task);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(
                    "IMPORT_PREVIEW_FAILED",
                    "导入文件预检失败：" + safeMessage(exception));
        }
    }

    public List<Map<String, Object>> listTasks() {
        return repository.list().stream().map(this::toMap).toList();
    }

    public Map<String, Object> getTask(String taskId) {
        return toMap(requireTask(taskId));
    }

    @Transactional
    public synchronized Map<String, Object> commitTask(String taskId, CurrentUser operator) {
        ImportTaskRecord task = requireTask(taskId);
        if ("COMMITTED".equals(task.status())) {
            return toMap(task);
        }
        if (!"PREVIEWED".equals(task.status())) {
            throw new BusinessException(
                    "IMPORT_TASK_STATE_INVALID",
                    "只有预检完成的任务可以提交",
                    HttpStatus.CONFLICT);
        }
        if (!task.fieldErrors().isEmpty()) {
            throw new BusinessException(
                    "IMPORT_VALIDATION_FAILED",
                    "预检仍存在字段错误，不能提交",
                    HttpStatus.CONFLICT);
        }
        List<ImportJournalEntry> journal = mutationService.applyTask(
                task.importType(),
                task.rows(),
                operator);
        ImportTaskRecord committed = task.committed(journal, Instant.now());
        repository.save(committed);
        return toMap(committed);
    }

    @Transactional
    public synchronized Map<String, Object> rollbackTask(String taskId, CurrentUser operator) {
        ImportTaskRecord task = requireTask(taskId);
        if ("ROLLED_BACK".equals(task.status())) {
            return toMap(task);
        }
        if (!"COMMITTED".equals(task.status())) {
            throw new BusinessException(
                    "IMPORT_TASK_STATE_INVALID",
                    "只有已经提交的任务可以回滚",
                    HttpStatus.CONFLICT);
        }
        mutationService.rollbackJournal(task.journal(), operator);
        ImportTaskRecord rolledBack = task.rolledBack(Instant.now());
        repository.save(rolledBack);
        return toMap(rolledBack);
    }

    public byte[] errorsCsv(String taskId) {
        ImportTaskRecord task = requireTask(taskId);
        StringBuilder csv = new StringBuilder("\uFEFFrow,field,value,message\n");
        for (Map<String, Object> error : task.fieldErrors()) {
            csv.append(csvValue(error.get("row"))).append(',')
                    .append(csvValue(error.get("field"))).append(',')
                    .append(csvValue(error.get("value"))).append(',')
                    .append(csvValue(error.get("message"))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<Map<String, Object>> validateRows(
            String importType,
            List<Map<String, String>> rows) {
        java.util.ArrayList<Map<String, Object>> errors = new java.util.ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            try {
                mutationService.validateRow(importType, rows.get(index));
            } catch (RuntimeException exception) {
                errors.add(error(
                        index + 2,
                        "row",
                        "",
                        safeMessage(exception)));
            }
        }
        return List.copyOf(errors);
    }

    private ImportTaskRecord requireTask(String taskId) {
        return repository.findById(taskId).orElseThrow(() -> new BusinessException(
                "IMPORT_TASK_NOT_FOUND",
                "导入任务不存在",
                HttpStatus.NOT_FOUND));
    }

    private Map<String, Object> toMap(ImportTaskRecord task) {
        int invalidRows = (int) task.fieldErrors().stream()
                .map(item -> item.get("row"))
                .distinct()
                .count();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("taskId", task.taskId());
        result.put("importType", task.importType());
        result.put("fileName", task.fileName());
        result.put("digestAlgorithm", DIGEST_ALGORITHM);
        result.put("digest", task.digest());
        result.put("idempotencyKey", task.idempotencyKey());
        result.put("status", task.status());
        result.put("totalRows", task.rows().size());
        result.put("validRows", Math.max(0, task.rows().size() - invalidRows));
        result.put("invalidRows", invalidRows);
        result.put("fieldErrors", task.fieldErrors());
        result.put("mutationCount", task.journal().size());
        result.put("createdAt", task.createdAt().toString());
        result.put("committedAt", task.committedAt() == null ? null : task.committedAt().toString());
        result.put("rolledBackAt", task.rolledBackAt() == null ? null : task.rolledBackAt().toString());
        return result;
    }

    private String normalizeType(String value) {
        String normalized = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (!List.of("STUDENT", "ROOM").contains(normalized)) {
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

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
                ? "导入校验失败"
                : throwable.getMessage();
    }
}
