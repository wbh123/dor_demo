package com.wust.dormitory.readiness;

import com.wust.dormitory.readiness.mapper.SystemReadinessMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class StudentReadinessChecker implements ReadinessChecker {
    private static final int SAMPLE_LIMIT = 10;
    private final SystemReadinessMapper mapper;

    public StudentReadinessChecker(SystemReadinessMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String category() {
        return "STUDENT";
    }

    @Override
    public boolean critical() {
        return true;
    }

    @Override
    public List<ReadinessCheckResult> check(ReadinessContext context) {
        Map<String, Object> data = mapper.studentSummary();
        long total = number(data, "totalStudents");
        long enabled = number(data, "enabledStudents");
        long unactivated = number(data, "unactivatedStudents");
        long invalidStudents = number(data, "invalidStudents");
        long missingCritical = number(data, "missingCriticalFields");
        long mapping = number(data, "missingMajorMapping");
        long degree = number(data, "invalidDegreeLevel");
        long gender = number(data, "invalidGender");
        List<Map<String, Object>> samples = invalidStudents == 0
                ? List.of()
                : mapper.studentIssueSamples(SAMPLE_LIMIT);

        boolean noStudents = total == 0;
        boolean invalidData = invalidStudents > 0;
        boolean blocked = noStudents || invalidData;
        String summary;
        String action;
        if (noStudents) {
            summary = "当前没有任何学生数据，无法开展单校选寝试点。";
            action = "先导入并核验本次试点学生数据";
        } else if (invalidData) {
            summary = "有 " + invalidStudents + " 名学生存在基础数据异常需要处理。";
            action = "修正学生基础字段、专业映射、培养层次或性别数据";
        } else {
            summary = "参与住宿分配所需的学生基础字段正常。";
            action = null;
        }
        ReadinessCheckResult quality = ReadinessCheckResult.of(
                "STUDENT_DATA_QUALITY", category(), "学生基础数据",
                blocked ? ReadinessSeverity.ERROR : ReadinessSeverity.PASS,
                blocked, blocked ? "FAILED" : "PASSED",
                summary,
                Map.of(
                        "totalStudents", total,
                        "enabledStudents", enabled,
                        "invalidStudents", invalidStudents,
                        "missingCriticalFields", missingCritical,
                        "missingMajorMapping", mapping,
                        "invalidDegreeLevel", degree,
                        "invalidGender", gender,
                        "sampleStudentNumbers", samples),
                action, "/admin/data", context.checkedAt());
        ReadinessCheckResult activation = unactivated == 0
                ? ReadinessCheckResult.of("STUDENT_ACTIVATION", category(), "学生账号激活", ReadinessSeverity.PASS,
                        false, "PASSED", "当前没有待激活学生账号。", Map.of("unactivatedStudents", 0), null, "/admin/data", context.checkedAt())
                : ReadinessCheckResult.of("STUDENT_ACTIVATION", category(), "学生账号激活", ReadinessSeverity.WARNING,
                        false, "ATTENTION", "有 " + unactivated + " 名学生尚未激活账号。",
                        Map.of("unactivatedStudents", unactivated), "开放前提醒学生完成账号激活", "/admin/data", context.checkedAt());
        return List.of(quality, activation);
    }

    private long number(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
