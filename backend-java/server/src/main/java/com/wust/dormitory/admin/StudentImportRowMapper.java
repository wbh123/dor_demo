package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class StudentImportRowMapper {
    private final NamedParameterJdbcTemplate jdbc;

    public StudentImportRowMapper(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StudentAdminService.StudentCommand map(Map<String, String> row) {
        String majorCode = value(row, "专业编码", "majorcode");
        List<Long> majorIds = jdbc.query(
                "SELECT id FROM major WHERE major_code=:code AND enabled=1",
                Map.of("code", majorCode),
                (rs, number) -> rs.getLong(1));
        if (majorIds.isEmpty()) {
            throw new BusinessException("MAJOR_NOT_AVAILABLE", "专业编码不存在或已禁用：" + majorCode);
        }
        String category = studentCategory(defaultValue(value(row, "学生类别", "studentcategory"), "DOMESTIC"));
        String nationalityText = firstValue(row,
                "国家/地区代码", "国家/地区", "国籍代码", "国籍", "nationalitycode", "countryregion");
        String nationality = CountryRegionCatalog.code(nationalityText, category);
        Integer gradeYear = nullableInteger(value(row, "年级", "gradeyear"));
        String degree = blankToNull(value(row, "培养层次", "degreelevel"));
        String phone = firstValue(row,
                "手机号码（含国家码）", "手机号（含国家码）", "手机号码", "手机号", "phonenumber");
        return new StudentAdminService.StudentCommand(
                value(row, "学号", "studentnumber"),
                value(row, "姓名", "studentname"),
                gender(defaultValue(value(row, "性别", "gender"), "M")),
                majorIds.getFirst(),
                nationality,
                category,
                "BATCH_IMPORT",
                PhoneNumberNormalizer.normalize(blankToNull(phone), nationality),
                degreeLevel(degree),
                gradeYear);
    }

    private String gender(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "男", "男生", "MALE", "M" -> "M";
            case "女", "女生", "FEMALE", "F" -> "F";
            default -> throw new BusinessException("STUDENT_GENDER_INVALID", "性别必须为男或女");
        };
    }

    private String studentCategory(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "国内生", "DOMESTIC" -> "DOMESTIC";
            case "国际生", "INTERNATIONAL" -> "INTERNATIONAL";
            default -> throw new BusinessException("STUDENT_CATEGORY_INVALID", "学生类别必须为国内生或国际生");
        };
    }

    private String degreeLevel(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "本科", "本科生", "UNDERGRADUATE" -> "UNDERGRADUATE";
            case "硕士", "硕士生", "MASTER" -> "MASTER";
            case "博士", "博士生", "DOCTOR" -> "DOCTOR";
            case "硕博", "硕博生", "MASTER_DOCTOR" -> "MASTER_DOCTOR";
            default -> throw new BusinessException("DEGREE_LEVEL_INVALID", "培养层次必须为本科生、硕士生、博士生或硕博生");
        };
    }

    private String value(Map<String, String> row, String chinese, String english) {
        String normalizedChinese = normalizeHeader(chinese);
        return row.getOrDefault(normalizedChinese, row.getOrDefault(normalizeHeader(english), "")).trim();
    }

    private String firstValue(Map<String, String> row, String... names) {
        for (String name : names) {
            String value = row.getOrDefault(normalizeHeader(name), "").trim();
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private String normalizeHeader(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("(", "（")
                .replace(")", "）");
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer nullableInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException exception) {
            throw new BusinessException("IMPORT_NUMBER_INVALID", "年级必须为整数");
        }
    }
}
