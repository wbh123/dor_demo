package com.wust.dormitory.export;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExportTaskService {
    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String SUCCEEDED = "SUCCEEDED";
    public static final String FAILED = "FAILED";
    public static final String CANCELLED = "CANCELLED";

    private final NamedParameterJdbcTemplate jdbc;

    public ExportTaskService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Map<String, Object> create(
            String taskType,
            String requestJson,
            String reason,
            CurrentUser operator) {
        String downloadToken = UUID.randomUUID().toString();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO export_task
                (task_type, task_status, request_json, progress,
                 requested_by, request_reason, download_token, expires_at)
                VALUES
                (:taskType,'QUEUED',CAST(:requestJson AS JSON),0,
                 :requestedBy,:reason,:downloadToken,
                 TIMESTAMPADD(HOUR,24,CURRENT_TIMESTAMP(3)))
                """, new MapSqlParameterSource()
                .addValue("taskType", taskType)
                .addValue("requestJson", requestJson)
                .addValue("requestedBy", operator == null ? null : operator.userId())
                .addValue("reason", reason)
                .addValue("downloadToken", downloadToken),
                keys,
                new String[]{"id"});
        Number key = keys.getKey();
        if (key == null) {
            throw new IllegalStateException("导出任务创建成功但未返回编号");
        }
        return get(key.longValue());
    }

    public List<Map<String, Object>> list(int page, int size) {
        int normalizedSize = Math.max(1, Math.min(size, 100));
        int offset = (Math.max(page, 1) - 1) * normalizedSize;
        return jdbc.queryForList("""
                SELECT id, task_type, task_status, progress, requested_by,
                       request_reason, file_name, file_size, error_code,
                       error_message, download_token AS downloadToken,
                       expires_at, created_at, started_at, completed_at
                FROM export_task
                ORDER BY id DESC
                LIMIT :size OFFSET :offset
                """, Map.of("size", normalizedSize, "offset", offset));
    }

    public Map<String, Object> get(long taskId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT id, task_type, task_status, request_json, progress,
                       requested_by, request_reason, file_name, file_size,
                       error_code, error_message, download_token AS downloadToken,
                       expires_at, created_at, started_at, completed_at
                FROM export_task WHERE id=:id
                """, Map.of("id", taskId));
        if (rows.isEmpty()) {
            throw new BusinessException("EXPORT_TASK_NOT_FOUND", "导出任务不存在", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    @Transactional
    public boolean claim(long taskId) {
        return jdbc.update("""
                UPDATE export_task
                SET task_status='RUNNING', started_at=CURRENT_TIMESTAMP(3), progress=1
                WHERE id=:id AND task_status='QUEUED'
                """, Map.of("id", taskId)) == 1;
    }

    public void progress(long taskId, int progress) {
        jdbc.update("""
                UPDATE export_task
                SET progress=:progress, updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id AND task_status='RUNNING'
                """, Map.of("id", taskId, "progress", Math.max(1, Math.min(progress, 99))));
    }

    public void succeed(long taskId, String fileName, long fileSize, String fileReference) {
        jdbc.update("""
                UPDATE export_task
                SET task_status='SUCCEEDED', progress=100,
                    file_name=:fileName, file_size=:fileSize,
                    file_reference=:fileReference,
                    completed_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id AND task_status='RUNNING'
                """, new MapSqlParameterSource()
                .addValue("id", taskId)
                .addValue("fileName", fileName)
                .addValue("fileSize", fileSize)
                .addValue("fileReference", fileReference));
    }

    public void fail(long taskId, String errorCode, String errorMessage) {
        jdbc.update("""
                UPDATE export_task
                SET task_status='FAILED', error_code=:errorCode,
                    error_message=:errorMessage,
                    completed_at=CURRENT_TIMESTAMP(3), updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id AND task_status IN ('QUEUED','RUNNING')
                """, Map.of(
                        "id", taskId,
                        "errorCode", errorCode == null ? "EXPORT_FAILED" : errorCode,
                        "errorMessage", errorMessage == null ? "导出失败" : errorMessage));
    }

    public void cancel(long taskId, CurrentUser operator) {
        int changed = jdbc.update("""
                UPDATE export_task
                SET task_status='CANCELLED', completed_at=CURRENT_TIMESTAMP(3),
                    updated_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id AND task_status='QUEUED'
                  AND (:operatorId IS NULL OR requested_by=:operatorId)
                """, Map.of(
                        "id", taskId,
                        "operatorId", operator == null ? null : operator.userId()));
        if (changed == 0) {
            throw new BusinessException(
                    "EXPORT_TASK_NOT_CANCELLABLE",
                    "只有尚未开始的导出任务可以取消",
                    HttpStatus.CONFLICT);
        }
    }

    public boolean downloadAvailable(Map<String, Object> task, LocalDateTime now) {
        if (!SUCCEEDED.equals(String.valueOf(task.get("task_status")))) {
            return false;
        }
        Object expires = task.get("expires_at");
        return !(expires instanceof LocalDateTime value) || value.isAfter(now);
    }
}
