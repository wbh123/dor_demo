package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/import")
public class AdminSpreadsheetController {
    private static final List<String> STUDENT_HEADERS = List.of("学号","姓名","性别","专业编码","国家/地区","学生类别","培养层次","年级","手机号");
    private static final List<String> STUDENT_EXAMPLE = List.of("202600000001","张三","男","SE","中国","国内生","硕士生","2026","13800000000");
    private static final List<String> ROOM_HEADERS = List.of("楼栋编码","楼栋名称","楼层","房间号","房型","容量","性别","学生类别","运行状态","备注");
    private static final List<String> ROOM_EXAMPLE = List.of("A","示例一号楼","1","101","FIVE_PERSON","5","F","MIXED","ENABLED","");

    private final StudentAdminService studentAdminService;
    private final RoomImportService roomImportService;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;

    public AdminSpreadsheetController(StudentAdminService studentAdminService, RoomImportService roomImportService,
                                      org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        this.studentAdminService = studentAdminService;
        this.roomImportService = roomImportService;
        this.jdbc = jdbc;
    }

    @PostMapping(value = "/students", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ObjectSuccessResponse> importStudents(@RequestParam("file") MultipartFile file) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        List<StudentAdminService.StudentCommand> commands = SpreadsheetSupport.read(file).stream().map(this::student).toList();
        if (commands.isEmpty()) throw new BusinessException("IMPORT_EMPTY", "文件中没有学生数据");
        return ResponseEntity.ok(ResponseFactory.object(studentAdminService.importStudents(commands, operator)));
    }

    @PostMapping(value = "/rooms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ObjectSuccessResponse> importRooms(@RequestParam("file") MultipartFile file) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        List<Map<String, String>> rows = SpreadsheetSupport.read(file);
        if (rows.isEmpty()) throw new BusinessException("IMPORT_EMPTY", "文件中没有宿舍数据");
        return ResponseEntity.ok(ResponseFactory.object(roomImportService.importRows(rows, operator)));
    }

    @GetMapping("/students/template")
    public ResponseEntity<byte[]> studentTemplate(@RequestParam(defaultValue = "xlsx") String format) {
        SecurityUsers.requireAdmin(); return template("学生导入模板", STUDENT_HEADERS, STUDENT_EXAMPLE, format);
    }

    @GetMapping("/rooms/template")
    public ResponseEntity<byte[]> roomTemplate(@RequestParam(defaultValue = "xlsx") String format) {
        SecurityUsers.requireAdmin(); return template("宿舍导入模板", ROOM_HEADERS, ROOM_EXAMPLE, format);
    }

    private ResponseEntity<byte[]> template(String name, List<String> headers, List<String> example, String format) {
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] content = csv ? SpreadsheetSupport.csvTemplate(headers, example) : SpreadsheetSupport.xlsxTemplate(name, headers, example);
        String filename = java.net.URLEncoder.encode(name + (csv ? ".csv" : ".xlsx"), StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(csv ? "text/csv;charset=UTF-8" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename).body(content);
    }

    private StudentAdminService.StudentCommand student(Map<String, String> row) {
        String majorCode = value(row, "专业编码", "majorcode");
        List<Long> majorIds = jdbc.query("SELECT id FROM major WHERE major_code=:code AND enabled=1", Map.of("code", majorCode), (rs, n) -> rs.getLong(1));
        if (majorIds.isEmpty()) throw new BusinessException("MAJOR_NOT_AVAILABLE", "专业编码不存在或已禁用：" + majorCode);
        String category = studentCategory(defaultValue(value(row, "学生类别", "studentcategory"), "DOMESTIC"));
        String nationalityText = defaultValue(value(row, "国家/地区", "countryregion"), value(row, "国籍", "nationalitycode"));
        String nationality = CountryRegionCatalog.code(nationalityText, category);
        Integer gradeYear = nullableInteger(value(row, "年级", "gradeyear"));
        String degree = blankToNull(value(row, "培养层次", "degreelevel"));
        return new StudentAdminService.StudentCommand(value(row,"学号","studentnumber"), value(row,"姓名","studentname"),
                gender(defaultValue(value(row,"性别","gender"),"M")), majorIds.getFirst(), nationality,
                category, "BATCH_IMPORT", blankToNull(value(row,"手机号","phonenumber")),
                degreeLevel(degree), gradeYear);
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

    private String value(Map<String, String> row, String zh, String en) {
        return row.getOrDefault(zh.toLowerCase(Locale.ROOT).replace(" ", "").replace("_", ""), row.getOrDefault(en, "")).trim();
    }
    private String defaultValue(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Integer nullableInteger(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value.trim()); } catch (NumberFormatException exception) { throw new BusinessException("IMPORT_NUMBER_INVALID", "年级必须为整数"); }
    }
}
