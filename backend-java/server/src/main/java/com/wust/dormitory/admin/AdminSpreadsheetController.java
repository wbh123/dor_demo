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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/import")
public class AdminSpreadsheetController {
    private static final List<String> STUDENT_HEADERS = List.of("学号", "姓名", "性别", "专业编码", "国家/地区", "学生类别", "培养层次", "年级", "手机号");
    private static final List<String> STUDENT_EXAMPLE = List.of("202600000001", "张三", "男", "SE", "中国", "国内生", "硕士生", "2026", "13800000000");
    private static final List<String> ROOM_HEADERS = List.of("楼栋编码", "楼栋名称", "楼层", "房间号", "房型", "容量", "性别", "学生类别", "运行状态", "备注");
    private static final List<String> ROOM_EXAMPLE = List.of("A", "示例一号楼", "1", "101", "FIVE_PERSON", "5", "F", "MIXED", "ENABLED", "");

    private final StudentAdminService studentAdminService;
    private final StudentImportRowMapper studentImportRowMapper;
    private final RoomImportService roomImportService;

    public AdminSpreadsheetController(
            StudentAdminService studentAdminService,
            StudentImportRowMapper studentImportRowMapper,
            RoomImportService roomImportService) {
        this.studentAdminService = studentAdminService;
        this.studentImportRowMapper = studentImportRowMapper;
        this.roomImportService = roomImportService;
    }

    @PostMapping(value = "/students", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ObjectSuccessResponse> importStudents(@RequestParam("file") MultipartFile file) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        List<StudentAdminService.StudentCommand> commands = SpreadsheetSupport.read(file).stream()
                .map(studentImportRowMapper::map)
                .toList();
        if (commands.isEmpty()) {
            throw new BusinessException("IMPORT_EMPTY", "文件中没有学生数据");
        }
        return ResponseEntity.ok(ResponseFactory.object(studentAdminService.importStudents(commands, operator)));
    }

    @PostMapping(value = "/rooms", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ObjectSuccessResponse> importRooms(@RequestParam("file") MultipartFile file) {
        CurrentUser operator = SecurityUsers.requireAdmin();
        List<Map<String, String>> rows = SpreadsheetSupport.read(file);
        if (rows.isEmpty()) {
            throw new BusinessException("IMPORT_EMPTY", "文件中没有宿舍数据");
        }
        return ResponseEntity.ok(ResponseFactory.object(roomImportService.importRows(rows, operator)));
    }

    @GetMapping("/students/template")
    public ResponseEntity<byte[]> studentTemplate(@RequestParam(defaultValue = "xlsx") String format) {
        SecurityUsers.requireAdmin();
        return template("学生导入模板", STUDENT_HEADERS, STUDENT_EXAMPLE, format);
    }

    @GetMapping("/rooms/template")
    public ResponseEntity<byte[]> roomTemplate(@RequestParam(defaultValue = "xlsx") String format) {
        SecurityUsers.requireAdmin();
        return template("宿舍导入模板", ROOM_HEADERS, ROOM_EXAMPLE, format);
    }

    private ResponseEntity<byte[]> template(String name, List<String> headers, List<String> example, String format) {
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] content = csv
                ? SpreadsheetSupport.csvTemplate(headers, example)
                : SpreadsheetSupport.xlsxTemplate(name, headers, example);
        String filename = java.net.URLEncoder.encode(name + (csv ? ".csv" : ".xlsx"), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(csv
                        ? "text/csv;charset=UTF-8"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(content);
    }
}
