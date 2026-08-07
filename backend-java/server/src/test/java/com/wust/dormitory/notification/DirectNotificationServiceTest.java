package com.wust.dormitory.notification;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import com.wust.dormitory.subscription.QuotaCodes;
import com.wust.dormitory.subscription.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectNotificationServiceTest {
    private FeatureAccessService featureAccessService;
    private SubscriptionService subscriptionService;
    private NotificationRecipientResolver recipientResolver;
    private NotificationService notificationService;
    private DirectNotificationService service;

    @BeforeEach
    void setUp() {
        featureAccessService = mock(FeatureAccessService.class);
        subscriptionService = mock(SubscriptionService.class);
        recipientResolver = mock(NotificationRecipientResolver.class);
        notificationService = mock(NotificationService.class);
        service = new DirectNotificationService(
                featureAccessService,
                subscriptionService,
                recipientResolver,
                notificationService);
        when(subscriptionService.currentSubscription()).thenReturn(new SubscriptionService.CurrentSubscription(
                1L, 1L, 1, 8L, "STANDARD", "标准版", 1,
                "LONG_TERM", "ACTIVE", null,
                LocalDateTime.of(2026, 1, 1, 0, 0), null, false));
        when(subscriptionService.quotasForPlanRevision(8L))
                .thenReturn(Map.of(QuotaCodes.MAX_NOTIFICATION_RECIPIENTS, 3L));
    }

    @Test
    void sendsLocalizedTextWithoutTemplate() {
        NotificationRecipientResolver.RecipientCriteria criteria = mock(NotificationRecipientResolver.RecipientCriteria.class);
        when(recipientResolver.resolve(criteria)).thenReturn(List.of(10L, 11L));
        CurrentUser operator = new CurrentUser(99L, null, "admin", "管理员", "ADMIN");

        Map<String, Object> result = service.send(
                criteria,
                "资料提醒",
                "请尽快完善个人资料。",
                "Profile reminder",
                "Please complete your profile.",
                "单独提醒学生完善资料",
                operator);

        assertEquals(2, result.get("recipientCount"));
        assertEquals("IN_APP", result.get("channel"));
        verify(featureAccessService).require(FeatureCodes.P3_NOTIFICATION_SEND);
        verify(notificationService).sendInAppBulk(
                List.of(10L, 11L),
                "ADMIN_NOTIFICATION",
                "notification.direct.title",
                "notification.direct.message",
                Map.of(
                        "directTitleZhCn", "资料提醒",
                        "directContentZhCn", "请尽快完善个人资料。",
                        "directTitleEnUs", "Profile reminder",
                        "directContentEnUs", "Please complete your profile.",
                        "operatorName", "管理员"));
    }

    @Test
    void rejectsRecipientsAboveConfiguredQuota() {
        NotificationRecipientResolver.RecipientCriteria criteria = mock(NotificationRecipientResolver.RecipientCriteria.class);
        when(recipientResolver.resolve(criteria)).thenReturn(List.of(1L, 2L, 3L, 4L));
        CurrentUser operator = new CurrentUser(99L, null, "admin", "管理员", "ADMIN");

        assertThrows(BusinessException.class, () -> service.send(
                criteria,
                "通知",
                "正文",
                "",
                "",
                "批量提醒",
                operator));
    }
}
