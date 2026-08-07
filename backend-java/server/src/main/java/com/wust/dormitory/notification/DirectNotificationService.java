package com.wust.dormitory.notification;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import com.wust.dormitory.subscription.QuotaCodes;
import com.wust.dormitory.subscription.SubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DirectNotificationService {
    private static final int DEFAULT_MAX_NOTIFICATION_RECIPIENTS = 1000;

    private final FeatureAccessService featureAccessService;
    private final SubscriptionService subscriptionService;
    private final NotificationRecipientResolver recipientResolver;
    private final NotificationService notificationService;

    public DirectNotificationService(
            FeatureAccessService featureAccessService,
            SubscriptionService subscriptionService,
            NotificationRecipientResolver recipientResolver,
            NotificationService notificationService) {
        this.featureAccessService = featureAccessService;
        this.subscriptionService = subscriptionService;
        this.recipientResolver = recipientResolver;
        this.notificationService = notificationService;
    }

    @Transactional
    public Map<String, Object> send(
            NotificationRecipientResolver.RecipientCriteria criteria,
            String titleZhCn,
            String contentZhCn,
            String titleEnUs,
            String contentEnUs,
            String reason,
            CurrentUser operator) {
        featureAccessService.require(FeatureCodes.P3_NOTIFICATION_SEND);
        List<Long> recipients = recipientResolver.resolve(criteria);
        enforceRecipientLimit(recipients.size());
        String normalizedTitle = requiredText(titleZhCn, "请填写通知标题");
        String normalizedContent = requiredText(contentZhCn, "请填写通知正文");
        requireReason(reason);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("directTitleZhCn", normalizedTitle);
        parameters.put("directContentZhCn", normalizedContent);
        parameters.put("directTitleEnUs", clean(titleEnUs));
        parameters.put("directContentEnUs", clean(contentEnUs));
        parameters.put("operatorName", operator.displayName());
        notificationService.sendInAppBulk(
                recipients,
                "ADMIN_NOTIFICATION",
                "notification.direct.title",
                "notification.direct.message",
                parameters);
        return Map.of(
                "recipientCount", recipients.size(),
                "channel", NotificationChannel.IN_APP.name(),
                "direct", true);
    }

    private void enforceRecipientLimit(int count) {
        SubscriptionService.CurrentSubscription current = subscriptionService.currentSubscription();
        long limit = subscriptionService.quotasForPlanRevision(current.planRevisionId())
                .getOrDefault(QuotaCodes.MAX_NOTIFICATION_RECIPIENTS,
                        (long) DEFAULT_MAX_NOTIFICATION_RECIPIENTS);
        if (count > limit) {
            throw new BusinessException(
                    "MAX_NOTIFICATION_RECIPIENTS_EXCEEDED",
                    "接收人数超过当前通知人数配额：" + limit);
        }
    }

    private String requiredText(String value, String message) {
        String normalized = clean(value);
        if (normalized.isEmpty()) {
            throw new BusinessException("NOTIFICATION_CONTENT_REQUIRED", message);
        }
        return normalized;
    }

    private String requireReason(String reason) {
        if (reason == null || reason.trim().length() < 2) {
            throw new BusinessException("NOTIFICATION_REASON_REQUIRED", "发送通知必须填写原因");
        }
        return reason.trim();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
