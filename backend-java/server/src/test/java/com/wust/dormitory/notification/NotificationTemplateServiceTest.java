package com.wust.dormitory.notification;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.subscription.FeatureAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class NotificationTemplateServiceTest {
    private NotificationTemplateService service;

    @BeforeEach
    void setUp() {
        service = new NotificationTemplateService(
                mock(NamedParameterJdbcTemplate.class),
                mock(FeatureAccessService.class));
    }

    @Test
    void rendersOnlyWhitelistedVariables() {
        String rendered = service.render(
                "{{studentName}}同学，{{batchName}}将于{{openAt}}开放。",
                Map.of("studentName", "张三", "batchName", "2026级选寝", "openAt", "18:30"));

        assertEquals("张三同学，2026级选寝将于18:30开放。", rendered);
    }

    @Test
    void rejectsExpressionAndUnknownVariable() {
        assertThrows(BusinessException.class,
                () -> service.render("{{databaseQuery}}", Map.of("databaseQuery", "SELECT *")));
        assertThrows(BusinessException.class,
                () -> service.render("{{student.name}}", Map.of()));
    }

    @Test
    void missingWhitelistedVariableRendersAsEmptyText() {
        assertEquals("同学您好", service.render("{{studentName}}同学您好", Map.of()));
    }
}
