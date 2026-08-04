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
        String majorCode = required(row, "专业编码").toUpperCase(Locale.ROOT);
        List<Long> majorIds = jdbc.query(
                "SELECT id FROM major WHERE major_code=:code AND enabled=1",
                Map.of("code", majorCode),
                (rs, number) -> rs.getLong(1));
        if (majorIds.isEmpty()) {
            throw new BusinessException("MAJOR_NOT_AVAILABLE", "专业编码不存在或已禁用：" + majorCode);
        }
        String category = studentCategory(required(row, "学生类别"));
        String nationality = CountryRegionCatalog.code(required(row, "国家/地区代码"), category);
        Integer gradeYear = nullableInteger(value(row, "年级"));
        String phone = value(row, "手机号码（含国家码）");
        return new StudentAdminService.StudentCommand(
                required(row, "学号"),
                required(row, "姓名"),
                gender(required(row, "性别")),
                majorIds.getFirst(),
                nationality,
                category,
                "BATCH_IMPORT",
                PhoneNumberNormalizer.normalize(blankToNull(phone), nationality),
                degreeLevel(blankToNull(value(row, "培养层次"))),
                gradeYear);
    }

    private String required(Map<String, String> row, String header) {
        String value = value(row, header);
        if (value.isBlank()) throw new BusinessException("IMPORT_FIELD_REQUIRED", header + "不能为空");
        return value;
    }

    private String value(Map<String, String> row, String header) {
        String value = row.get(header);
        return value == null ? "" : value.trim();
    }

    private String gender(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "男", "M" -> "M";
            case "女", "F" -> "F";
            default -> throw new BusinessException("STUDENT_GENDER_INVALID", "性别必须填写男、女、M或F");
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
            case "本科生", "UNDERGRADUATE" -> "UNDERGRADUATE";
            case "硕士生", "MASTER" -> "MASTER";
            case "博士生", "DOCTOR" -> "DOCTOR";
            case "硕博生", "MASTER_DOCTOR" -> "MASTER_DOCTOR";
            default -> throw new BusinessException("DEGREE_LEVEL_INVALID", "培养层次必须为本科生、硕士生、博士生或硕博生");
        };
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
