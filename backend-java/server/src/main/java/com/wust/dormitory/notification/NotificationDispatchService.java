package com.wust.dormitory.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.notification.mapper.NotificationDispatchMapper;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import com.wust.dormitory.subscription.QuotaCodes;
import com.wust.dormitory.subscription.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationDispatchService {
    private static final int DEFAULT_MAX_NOTIFICATION_RECIPIENTS = 1000;
    private static final int CHUNK_SIZE = 200;

    private final NotificationDispatchMapper mapper;
    private final FeatureAccessService featureAccessService;
    private final SubscriptionService subscriptionService;
    private final NotificationRecipientResolver recipientResolver;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    public NotificationDispatchService(
            NotificationDispatchMapper mapper,
            FeatureAccessService featureAccessService,
            SubscriptionService subscriptionService,
            NotificationRecipientResolver recipientResolver,
            NotificationService notificationService,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
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
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("recipientCount", recipients.size());
        preview.put("templateRevisionId", templateRevisionId);
        preview.put("titleZhCn", template.get("title_zh_cn"));
        preview.put("contentZhCn", template.get("content_zh_cn"));
        preview.put("titleEnUs", template.get("title_en_us"));
        preview.put("contentEnUs", template.get("content_en_us"));
        preview.put("variables", variables == null ? Map.of() : variables);
        preview.put("channel", NotificationChannel.IN_APP.name());
        preview.put("externalChannelsImplemented", false);
        return preview;
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
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("executionKey", executionKey);
        task.put("templateRevisionId", templateRevisionId);
        task.put("criteria", json(criteria));
        task.put("variables", json(variables == null ? Map.of() : variables));
        task.put("recipientCount", recipients.size());
        task.put("scheduledAt", executeAt);
        task.put("timeZone", zone.getId());
        task.put("chunkSize", CHUNK_SIZE);
        task.put("createdBy", operator.userId());
        task.put("reason", requireReason(reason));
        mapper.insertTask(task);
        Object rawTaskId = task.get("id");
        if (!(rawTaskId instanceof Number taskId)) {
            throw new IllegalStateException("通知任务未返回编号");
        }
        if (!recipients.isEmpty()) {
            mapper.insertRecipients(taskId.longValue(), recipients);
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
        int completed = 0;
        for (Map<String, Object> task : mapper.findDueTasks()) {
            long taskId = ((Number) task.get("id")).longValue();
            if (mapper.claimTask(taskId) != 1) {
                continue;
            }
            try {
                dispatchTask(
                        taskId,
                        ((Number) task.get("template_revision_id")).longValue(),
                        task.get("variables_json"));
                mapper.markSucceeded(taskId);
                completed++;
            } catch (RuntimeException exception) {
                mapper.markFailed(taskId, exception.getMessage());
            }
        }
        return completed;
    }

    public void cancel(long taskId, CurrentUser operator) {
        featureAccessService.require(FeatureCodes.P3_NOTIFICATION_SCHEDULE);
        if (mapper.cancelScheduledTask(taskId, operator.userId()) == 0) {
            throw new BusinessException(
                    "NOTIFICATION_TASK_NOT_CANCELLABLE",
                    "只有尚未开始的定时通知可以取消",
                    HttpStatus.CONFLICT);
        }
    }

    public List<Map<String, Object>> status(int page, int size) {
        featureAccessService.require(FeatureCodes.P3_NOTIFICATION_DELIVERY_STATUS);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        return mapper.findStatusPage(
                normalizedSize,
                (Math.max(1, page) - 1) * normalizedSize);
    }

    private void dispatchTask(long taskId, long templateRevisionId, Object rawVariables) {
        Map<String, Object> template = templateRevision(templateRevisionId);
        Map<String, Object> variables = parseMap(rawVariables);
        while (true) {
            List<Long> recipients = mapper.findPendingRecipients(taskId, CHUNK_SIZE);
            if (recipients.isEmpty()) {
                return;
            }
            notificationService.sendInAppBulk(
                    recipients,
                    "ADMIN_NOTIFICATION",
                    String.valueOf(template.get("title_key")),
                    String.valueOf(template.get("message_key")),
                    variables);
            mapper.markRecipientsDelivered(taskId, recipients);
        }
    }

    private Map<String, Object> templateRevision(long templateRevisionId) {
        Map<String, Object> template = mapper.findTemplateRevision(templateRevisionId);
        if (template == null || template.isEmpty()) {
            throw new BusinessException(
                    "NOTIFICATION_TEMPLATE_NOT_FOUND",
                    "通知模板修订不存在或已停用",
                    HttpStatus.NOT_FOUND);
        }
        return template;
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
