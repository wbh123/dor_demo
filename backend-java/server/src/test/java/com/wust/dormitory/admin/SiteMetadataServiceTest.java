package com.wust.dormitory.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.admin.mapper.SiteMetadataMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SiteMetadataServiceTest {
    private SiteMetadataMapper mapper;
    private SiteMetadataService service;
    private CurrentUser admin;

    @BeforeEach
    void setUp() {
        mapper = mock(SiteMetadataMapper.class);
        service = new SiteMetadataService(
                mapper,
                new ObjectMapper(),
                mock(AuditService.class),
                "示例大学");
        admin = new CurrentUser(7L, null, "admin", "管理员", "ADMIN");
        when(mapper.findValue("LOGIN_LEFT_ADMIN_EDITABLE")).thenReturn("true");
    }

    @Test
    void rejectsExecutableOrAttributeBearingLoginHtml() {
        List<String> unsafeSamples = List.of(
                "<script>alert(1)</script>",
                "<p onclick=\"alert(1)\">欢迎</p>",
                "<img src=x onerror=\"alert(1)\">",
                "<iframe src=\"https://example.invalid\"></iframe>",
                "<object data=\"x\"></object>",
                "<embed src=\"x\">",
                "<a href=\"javascript:alert(1)\">点击</a>",
                "<svg/onload=alert(1)>");

        for (String html : unsafeSamples) {
            assertThatThrownBy(() -> service.updateLoginForSchoolAdmin(
                    new SiteMetadataService.LoginContentCommand(html, ""),
                    admin))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("HTML");
        }

        verify(mapper, never()).upsert(eq("LOGIN_LEFT_CONTENT"), anyString(), anyLong());
    }

    @Test
    void acceptsDefinedSafeFormattingTagsWithoutAttributes() {
        String html = "<h1>欢迎入住</h1><p>请先阅读<strong>选寝须知</strong>。</p>"
                + "<ul><li>完善偏好</li><li><em>确认</em>床位</li></ul><br/>";

        assertThatCode(() -> service.updateLoginForSchoolAdmin(
                new SiteMetadataService.LoginContentCommand(html, ""),
                admin)).doesNotThrowAnyException();

        verify(mapper).upsert(eq("LOGIN_LEFT_CONTENT"), anyString(), eq(7L));
    }
}
