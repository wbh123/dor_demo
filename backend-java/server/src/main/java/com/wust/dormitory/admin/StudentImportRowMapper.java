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
        String majorCode = normalizeText(required(row, "专业编码"))
                .toUpperCase(Locale.ROOT);
        List<Long> majorIds = jdbc.query(
                "SELECT id FROM major WHERE major_code=:code AND enabled=1",
                Map.of("code", majorCode),
                (rs, number) -> rs.getLong(1));
        if (majorIds.isEmpty()) {
            throw new BusinessException(
                    "MAJOR_NOT_AVAILABLE",
                    "专业编码不存在或已禁用：" + majorCode);
        }

        String category = normalizeStudentCategory(required(row, "学生类别"));
        String nationality = normalizeCountryCode(
                required(row, "国家/地区代码"),
                category);
        Integer gradeYear = normalizeGradeYear(value(row, "年级"));
        String phone = value(row, "手机号码（含国家码）");
        return new StudentAdminService.StudentCommand(
                normalizeStudentNumber(required(row, "学号")),
                normalizeText(required(row, "姓名")),
                normalizeGender(required(row, "性别")),
                majorIds.getFirst(),
                nationality,
                category,
                "BATCH_IMPORT",
                PhoneNumberNormalizer.normalize(blankToNull(phone), nationality),
                normalizeDegreeLevel(blankToNull(value(row, "培养层次"))),
                gradeYear);
    }

    String normalizeStudentCategory(String value) {
        return switch (normalizeText(value).toUpperCase(Locale.ROOT)) {
            case "国内生", "中国学生", "DOMESTIC" -> "DOMESTIC";
            case "国际生", "留学生", "INTERNATIONAL" -> "INTERNATIONAL";
            default -> throw new BusinessException(
                    "STUDENT_CATEGORY_INVALID",
                    "学生类别必须为国内生、国际生、DOMESTIC或INTERNATIONAL");
        };
    }

    String normalizeCountryCode(String value, String category) {
        return CountryRegionCatalog.code(normalizeText(value), category);
    }

    String normalizeDegreeLevel(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (normalizeText(value).toUpperCase(Locale.ROOT)) {
            case "本科", "本科生", "UNDERGRADUATE" -> "UNDERGRADUATE";
            case "硕士", "硕士生", "MASTER" -> "MASTER";
            case "博士", "博士生", "DOCTOR" -> "DOCTOR";
            case "硕博", "硕博生", "硕博连读", "MASTER_DOCTOR" -> "MASTER_DOCTOR";
            default -> throw new BusinessException(
                    "DEGREE_LEVEL_INVALID",
                    "培养层次必须为本科生、硕士生、博士生、硕博生或对应规范代码");
        };
    }

    Integer normalizeGradeYear(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = normalizeText(value)
                .replace("年级", "")
                .replace("级", "")
                .trim();
        if (!normalized.matches("\\d{4}")) {
            throw new BusinessException(
                    "IMPORT_NUMBER_INVALID",
                    "年级必须为四位年份，例如2026或2026级");
        }
        int year = Integer.parseInt(normalized);
        if (year < 2000 || year > 2100) {
            throw new BusinessException(
                    "GRADE_YEAR_INVALID",
                    "年级必须在2000至2100之间");
        }
        return year;
    }

    private String normalizeGender(String value) {
        return switch (normalizeText(value).toUpperCase(Locale.ROOT)) {
            case "男", "男生", "M", "MALE" -> "M";
            case "女", "女生", "F", "FEMALE" -> "F";
            default -> throw new BusinessException(
                    "STUDENT_GENDER_INVALID",
                    "性别必须填写男、女、M或F");
        };
    }

    private String normalizeStudentNumber(String value) {
        String normalized = value
                .replace("\u3000", "")
                .replaceAll("\\s+", "")
                .trim();
        if (!normalized.matches("\\d{12}")) {
            throw new BusinessException(
                    "STUDENT_NUMBER_INVALID",
                    "学号必须为12位数字");
        }
        return normalized;
    }

    private String required(Map<String, String> row, String header) {
        String value = value(row, header);
        if (value.isBlank()) {
            throw new BusinessException("IMPORT_FIELD_REQUIRED", header + "不能为空");
        }
        return value;
    }

    private String value(Map<String, String> row, String header) {
        String value = row.get(header);
        return value == null ? "" : normalizeText(value);
    }

    private String normalizeText(String value) {
        return value == null
                ? ""
                : value.replace('\u3000', ' ').trim().replaceAll("\\s+", " ");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : normalizeText(value);
    }
}
