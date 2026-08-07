package com.wust.dormitory.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import com.wust.dormitory.subscription.QuotaCodes;
import com.wust.dormitory.subscription.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationDispatchServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private FeatureAccessService featureAccessService;
    private SubscriptionService subscriptionService;
    private NotificationRecipientResolver recipientResolver;
    private NotificationService notificationService;
    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        featureAccessService = mock(FeatureAccessService.class);
        subscriptionService = mock(SubscriptionService.class);
        recipientResolver = mock(NotificationRecipientResolver.class);
        notificationService = mock(NotificationService.class);
        service = new NotificationDispatchService(
                jdbc, featureAccessService, subscriptionService,
                recipientResolver, notificationService, new ObjectMapper());
        when(subscriptionService.currentSubscription()).thenReturn(new SubscriptionService.CurrentSubscription(
                1L, 1L, 1, 8L, "STANDARD", "标准版", 1,
                "LONG_TERM", "ACTIVE", null,
                LocalDateTime.of(2026, 1, 1, 0, 0), null, false));
        when(subscriptionService.quotasForPlanRevision(8L))
                .thenReturn(Map.of(QuotaCodes.MAX_NOTIFICATION_RECIPIENTS, 3L));
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "id", 5L,
                "title_zh_cn", "通知",
                "content_zh_cn", "内容",
                "title_en_us", "Notice",
                "content_en_us", "Content",
                "title_key", "notification.template.test.title",
                "message_key", "notification.template.test.message")));
    }

    @Test
    void preflightReturnsInAppPreviewWithinRecipientQuota() {
        NotificationRecipientResolver.RecipientCriteria criteria = mock(NotificationRecipientResolver.RecipientCriteria.class);
        when(recipientResolver.resolve(criteria)).thenReturn(List.of(10L, 11L, 12L));

        Map<String, Object> result = service.preflight(criteria, 5L, Map.of("batchName", "秋季选寝"));

        assertEquals(3, result.get("recipientCount"));
        assertEquals("IN_APP", result.get("channel"));
        assertFalse((Boolean) result.get("externalChannelsImplemented"));
        verify(featureAccessService).require(FeatureCodes.P3_NOTIFICATION_SEND);
    }

    @Test
    void preflightAllowsTemplateWithMissingEnglishTranslation() {
        NotificationRecipientResolver.RecipientCriteria criteria = mock(NotificationRecipientResolver.RecipientCriteria.class);
        when(recipientResolver.resolve(criteria)).thenReturn(List.of(10L));
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("id", 5L);
        template.put("title_zh_cn", "单独提醒");
        template.put("content_zh_cn", "请尽快完善资料");
        template.put("title_en_us", null);
        template.put("content_en_us", null);
        template.put("title_key", "notification.template.test.title");
        template.put("message_key", "notification.template.test.message");
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(template));

        Map<String, Object> result = service.preflight(criteria, 5L, Map.of());

        assertEquals("单独提醒", result.get("titleZhCn"));
        assertEquals("请尽快完善资料", result.get("contentZhCn"));
        assertNull(result.get("titleEnUs"));
        assertNull(result.get("contentEnUs"));
    }

    @Test
    void preflightRejectsRecipientsAboveConfiguredQuota() {
        NotificationRecipientResolver.RecipientCriteria criteria = mock(NotificationRecipientResolver.RecipientCriteria.class);
        when(recipientResolver.resolve(criteria)).thenReturn(List.of(1L, 2L, 3L, 4L));

        assertThrows(BusinessException.class, () -> service.preflight(criteria, 5L, Map.of()));
    }

    @Test
    void anotherWorkerCannotDispatchTaskAfterAtomicClaimFails() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(Map.of(
                "id", 99L,
                "executionKey", "unique-execution-key",
                "template_revision_id", 5L,
                "variables_json", "{}")));
        when(jdbc.update(anyString(), anyMap())).thenReturn(0);

        int completed = service.dispatchDue();

        assertEquals(0, completed);
        verifyNoInteractions(notificationService);
    }
}
