package com.wust.dormitory.auth;

import com.wust.dormitory.admin.SystemSettingService;
import com.wust.dormitory.model.dto.WelcomeData;
import com.wust.dormitory.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentWelcomeServiceTest {
    @Test
    void rendersAdministratorManagedLocaleMessages() {
        TestFixture fixture = fixture("CN");
        when(fixture.settingService.readConfiguration("{}"))
                .thenReturn(configuration());

        WelcomeData result = fixture.service.welcomeFor(studentUser());

        assertThat(result.getMessage())
                .isEqualTo("欢迎张同学，你是2026计算机科学与技术的硕士生。");
        assertThat(result.getMessages())
                .containsEntry("en-US", "Welcome 张同学 (202600000001).")
                .containsEntry("ja-JP", "张同学さん、ようこそ。")
                .doesNotContainKey("countryMessages");
    }

    @Test
    void foreignStudentLegacyMessageFallsBackToAdministratorEnglishVersion() {
        TestFixture fixture = fixture("JP");
        when(fixture.settingService.readConfiguration("{}"))
                .thenReturn(configuration());

        WelcomeData result = fixture.service.welcomeFor(studentUser());

        assertThat(result.getMessage())
                .isEqualTo("Welcome 张同学 (202600000001).");
        assertThat(result.getMessages().get("ja-JP"))
                .isEqualTo("张同学さん、ようこそ。");
    }

    private TestFixture fixture(String nationalityCode) {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        SystemSettingService settingService = mock(SystemSettingService.class);
        StudentWelcomeService service = new StudentWelcomeService(jdbc, settingService);

        Map<String, Object> student = new LinkedHashMap<>();
        student.put("welcome_acknowledged_at", null);
        student.put("student_number", "202600000001");
        student.put("student_name", "张同学");
        student.put("nationality_code", nationalityCode);
        student.put("grade_year", 2026);
        student.put("degree_level", "MASTER");
        student.put("major_name", "计算机科学与技术");
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of(student));
        when(jdbc.query(anyString(), anyMap(), any(ResultSetExtractor.class))).thenReturn("{}");
        return new TestFixture(service, settingService);
    }

    private SystemSettingService.WelcomeConfiguration configuration() {
        return new SystemSettingService.WelcomeConfiguration(Map.of(
                "zh-CN", "欢迎{{学生姓名}}，你是{{年级}}{{专业名称}}的{{培养层次}}。",
                "en-US", "Welcome {{学生姓名}} ({{学号}}).",
                "ja-JP", "{{学生姓名}}さん、ようこそ。"));
    }

    private CurrentUser studentUser() {
        return new CurrentUser(1L, 2L, "202600000001", "张同学", "STUDENT");
    }

    private record TestFixture(
            StudentWelcomeService service,
            SystemSettingService settingService) { }
}
