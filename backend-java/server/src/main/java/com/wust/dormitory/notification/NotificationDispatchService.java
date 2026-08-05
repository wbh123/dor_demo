package com.wust.dormitory.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import com.wust.dormitory.subscription.QuotaCodes;
import com.wust.dormitory.subscription.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationDispatchService {
    private static final int DEFAULT_MAX_NOTIFICATION_RECIPIENTS = 1000;
    private static final int CHUNK_SIZE = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureAccessService featureAccessService;
    private final SubscriptionService subscriptionService;
    private final NotificationRecipientResolver recipientResolver;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationDispatchService(
            NamedParameterJdbcTemplate jdbc,
            FeatureAccessService featureAccessService,
            SubscriptionService subscriptionService,
            NotificationRecipientResolver recipientResolver,
            NotificationService notificationService,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.featureAccessService = featureAccessService;
        this.subscriptionService = subscriptionService;
        this.recipientResolver = recipientResolver;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> preflight(
            NotificationRecipientResolver.RecipientCriteria criteria,
            long templateRevisionId,
            Map<String, ?> variables) {
        featureAccessService.require(FeatureCodes.P3_NOTIFICATION_SEND);
        List<Long> recipients = recipientResolver.resolve(criteria);
        enforceRecipientLimit(recipients.size());
        Map<String, Object> template = templateRevision(templateRevisionId);
        return Map.of(
                "recipientCount", recipients.size(),
                "templateRevisionId", templateRevisionId,
                "titleZhCn", template.get("title_zh_cn"),
                "contentZhCn", template.get("content_zh_cn"),
                "titleEnUs", template.get("title_en_us"),
                "contentEnUs", template.get("content_en_us"),
                "variables", variables == null ? Map.of() : variables,
                "channel", NotificationChannel.IN_APP.name(),
                "externalChannelsImplemented", false);
    }

    @Transactional
    public Map<String, Object> schedule(
            NotificationRecipientResolver.RecipientCriteria criteria,
            long templateRevisionId,
            Map<String, ?> variables,
            LocalDateTime scheduledAt,
            String zoneId,
            String reason,
            CurrentUser operator) {
        featureAccessService.require(
                scheduledAt == null || !scheduledAt.isAfter(LocalDateTime.now())
                        ? FeatureCodes.P3_NOTIFICATION_SEND
                        : FeatureCodes.P3_NOTIFICATION_SCHEDULE);
        List<Long> recipients = recipientResolver.resolve(criteria);
        enforceRecipientLimit(recipients.size());
        templateRevision(templateRevisionId);
        String executionKey = UUID.randomUUID().toString();
        ZoneId zone = ZoneId.of(zoneId == null || zoneId.isBlank() ? "Asia/Shanghai" : zoneId);
        LocalDateTime executeAt = scheduledAt == null ? LocalDateTime.now() : scheduledAt;
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO notification_send_task
                (execution_key, task_status, channel, template_revision_id,
                 recipient_criteria_json, variables_json, recipient_count,
                 scheduled_at, time_zone, chunk_size, created_by, creation_reason)
                VALUES
                (:executionKey,'SCHEDULED','IN_APP',:templateRevisionId,
                 CAST(:criteria AS JSON),CAST(:variables AS JSON),:recipientCount,
                 :scheduledAt,:timeZone,:chunkSize,:createdBy,:reason)
                """, new MapSqlParameterSource()
                .addValue("executionKey", executionKey)
                .addValue("templateRevisionId", templateRevisionId)
                .addValue("criteria", json(criteria))
                .addValue("variables", json(variables == null ? Map.of() : variables))
                .addValue("recipientCount", recipients.size())
                .addValue("scheduledAt", executeAt)
                .addValue("timeZone", zone.getId())
                .addValue("chunkSize", CHUNK_SIZE)
                .addValue("createdBy", operator.userId())
                .addValue("reason", requireReason(reason)),
                keys,
                new String[]{"id"});
        Number taskId = keys.getKey();
        if (taskId == null) throw new IllegalStateException("通知任务未返回编号");
        List<MapSqlParameterSource> rows = new ArrayList<>();
        for (Long studentId : recipients) {
            rows.add(new MapSqlParameterSource()
                    .addValue("taskId", taskId.longValue())
                    .addValue("studentId", studentId));
        }
        if (!rows.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO notification_recipient
                    (send_task_id, student_id, delivery_status)
                    VALUES (:taskId,:studentId,'PENDING')
                    """, rows.toArray(MapSqlParameterSource[]::new));
        }
        return Map.of(
                "id", taskId.longValue(),
                "executionKey", executionKey,
                "recipientCount", recipients.size(),
                "scheduledAt", executeAt,
                "timeZone", zone.getId(),
                "channel", NotificationChannel.IN_APP.name());
    }

    @Transactional
    public int dispatchDue() {
        List<Map<String, Object>> tasks = jdbc.queryForList("""
                SELECT id, execution_key AS executionKey, template_revision_id,
                       variables_json
                FROM notification_send_task
                WHERE task_status='SCHEDULED'
                  AND scheduled_at<=CURRENT_TIMESTAMP(3)
                ORDER BY scheduled_at, id
                LIMIT 20
                FOR UPDATE SKIP LOCKED
                """, Map.of());
        int completed = 0;
        for (Map<String, Object> task : tasks) {
            long taskId = ((Number) task.get("id")).longValue();
            if (jdbc.update("""
                    UPDATE notification_send_task
                    SET task_status='RUNNING', started_at=CURRENT_TIMESTAMP(3)
                    WHERE id=:id AND task_status='SCHEDULED'
                    """, Map.of("id", taskId)) != 1) {
                continue;
            }
            try {
                dispatchTask(taskId, ((Number) task.get("template_revision_id")).longValue(), task.get("variables_json"));
                jdbc.update("""
                        UPDATE notification_send_task
                        SET task_status='SUCCEEDED', completed_at=CURRENT_TIMESTAMP(3)
                        WHERE id=:id AND task_status='RUNNING'
                        """, Map.of("id", taskId));
                completed++;
            } catch (RuntimeException exception) {
                jdbc.update("""
                        UPDATE notification_send_task
                        SET task_status='FAILED', failure_reason=:reason,
                            completed_at=CURRENT_TIMESTAMP(3)
                        WHERE id=:id
                        """, Map.of("id", taskId, "reason", exception.getMessage()));
            }
        }
        return completed;
    }

    public void cancel(long taskId, CurrentUser operator) {
        featureAccessService.require(FeatureCodes.P3_NOTIFICATION_SCHEDULE);
        int changed = jdbc.update("""
                UPDATE notification_send_task
                SET task_status='CANCELLED', cancelled_by=:operatorId,
                    cancelled_at=CURRENT_TIMESTAMP(3)
                WHERE id=:id AND task_status='SCHEDULED'
                """, Map.of("id", taskId, "operatorId", operator.userId()));
        if (changed == 0) {
            throw new BusinessException(
                    "NOTIFICATION_TASK_NOT_CANCELLABLE",
                    "只有尚未开始的定时通知可以取消",
                    HttpStatus.CONFLICT);
        }
    }

    public List<Map<String, Object>> status(int page, int size) {
        featureAccessService.require(FeatureCodes.P3_NOTIFICATION_DELIVERY_STATUS);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        return jdbc.queryForList("""
                SELECT task.id, task.execution_key, task.task_status, task.channel,
                       task.recipient_count, task.scheduled_at, task.time_zone,
                       task.failure_reason, task.created_at, task.started_at,
                       task.completed_at,
                       SUM(recipient.delivery_status='DELIVERED') AS delivered_count,
                       SUM(recipient.delivery_status='FAILED') AS failed_count
                FROM notification_send_task task
                LEFT JOIN notification_recipient recipient
                  ON recipient.send_task_id=task.id
                GROUP BY task.id
                ORDER BY task.id DESC
                LIMIT :size OFFSET :offset
                """, Map.of(
                        "size", normalizedSize,
                        "offset", (Math.max(1, page) - 1) * normalizedSize));
    }

    private void dispatchTask(long taskId, long templateRevisionId, Object rawVariables) {
        Map<String, Object> template = templateRevision(templateRevisionId);
        Map<String, Object> variables = parseMap(rawVariables);
        while (true) {
            List<Long> recipients = jdbc.queryForList("""
                    SELECT student_id
                    FROM notification_recipient
                    WHERE send_task_id=:taskId AND delivery_status='PENDING'
                    ORDER BY id
                    LIMIT :chunk
                    """, Map.of("taskId", taskId, "chunk", CHUNK_SIZE), Long.class);
            if (recipients.isEmpty()) break;
            notificationService.sendInAppBulk(
                    recipients,
                    "ADMIN_NOTIFICATION",
                    String.valueOf(template.get("title_key")),
                    String.valueOf(template.get("message_key")),
                    variables);
            jdbc.update("""
                    UPDATE notification_recipient
                    SET delivery_status='DELIVERED', delivered_at=CURRENT_TIMESTAMP(3)
                    WHERE send_task_id=:taskId AND student_id IN (:studentIds)
                      AND delivery_status='PENDING'
                    """, new MapSqlParameterSource()
                    .addValue("taskId", taskId)
                    .addValue("studentIds", recipients));
        }
    }

    private Map<String, Object> templateRevision(long templateRevisionId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT revision.id, revision.title_zh_cn, revision.content_zh_cn,
                       revision.title_en_us, revision.content_en_us,
                       CONCAT('notification.template.',template.template_code,'.title') AS title_key,
                       CONCAT('notification.template.',template.template_code,'.message') AS message_key
                FROM notification_template_revision revision
                JOIN notification_template template ON template.id=revision.template_id
                WHERE revision.id=:id AND template.enabled=1
                """, Map.of("id", templateRevisionId));
        if (rows.isEmpty()) {
            throw new BusinessException("NOTIFICATION_TEMPLATE_NOT_FOUND", "通知模板修订不存在或已停用", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void enforceRecipientLimit(int count) {
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        long limit = subscriptionService.quotasForPlanRevision(current.planRevisionId())
                .getOrDefault(QuotaCodes.MAX_NOTIFICATION_RECIPIENTS,
                        (long) DEFAULT_MAX_NOTIFICATION_RECIPIENTS);
        if (count > limit) {
            throw new BusinessException(
                    "MAX_NOTIFICATION_RECIPIENTS_EXCEEDED",
                    "接收人数超过当前通知人数配额：" + limit,
                    HttpStatus.CONFLICT);
        }
    }

    private Map<String, Object> parseMap(Object raw) {
        if (raw == null) return Map.of();
        try {
            if (raw instanceof String text) {
                return objectMapper.readValue(text, new TypeReference<>() {});
            }
            return objectMapper.convertValue(raw, new TypeReference<>() {});
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            throw new IllegalArgumentException("通知变量格式无效", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("通知任务参数无法序列化", exception);
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.trim().length() < 2) {
            throw new BusinessException("NOTIFICATION_REASON_REQUIRED", "发送通知必须填写原因");
        }
        return reason.trim();
    }
}
