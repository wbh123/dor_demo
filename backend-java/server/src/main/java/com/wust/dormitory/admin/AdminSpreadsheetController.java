package com.wust.dormitory.admin;

import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/import")
public class AdminSpreadsheetController {
    private static final List<String> STUDENT_HEADERS = List.of(
            "学号", "姓名", "性别", "专业编码", "国家/地区代码", "学生类别",
            "培养层次", "年级", "手机号码（含国家码）");
    private static final List<String> STUDENT_EXAMPLE = List.of(
            "202600000001", "张三", "男", "SE", "CN", "国内生",
            "硕士生", "2026", "+8613800000000");
    private static final List<String> STUDENT_INSTRUCTIONS = List.of(
            "本模板是当前系统唯一支持的学生导入模板，不兼容任何旧模板或历史字段名。",
            "请在“数据填写”工作表中录入学生，不得修改、重排、删除或新增表头。",
            "学号必须为12位数字；专业编码必须与系统中已启用的专业一致。",
            "手机号码统一填写为“+国家码+号码”，例如+8613800000000。",
            "上传后先进入导入任务预检，预检通过并确认后才会写入正式数据。"
    );
    private static final List<String> ROOM_HEADERS = List.of(
            "楼栋编码", "楼栋名称", "楼层", "房间号", "房型", "容量",
            "性别", "学生类别", "运行状态", "备注");
    private static final List<String> ROOM_EXAMPLE = List.of(
            "A", "示例一号楼", "1", "101", "FIVE_PERSON", "5",
            "F", "MIXED", "ENABLED", "示例数据，请按实际情况修改");
    private static final List<String> ROOM_INSTRUCTIONS = List.of(
            "本模板是当前系统唯一支持的宿舍导入模板，不兼容任何旧模板或历史字段名。",
            "请在“数据填写”工作表中录入宿舍，不得修改、重排、删除或新增表头。",
            "楼栋编码、楼层和房间号共同确定一间宿舍。",
            "性别填写M或F；学生类别填写DOMESTIC_ONLY、INTERNATIONAL_ONLY或MIXED。",
            "上传后先进入导入任务预检，预检通过并确认后才会写入正式数据。"
    );
    private static final List<List<String>> COUNTRY_DICTIONARY = List.of(
            country("CN", "中国大陆"), country("HK", "中国香港"), country("MO", "中国澳门"), country("TW", "中国台湾"),
            country("JP", "日本"), country("KR", "韩国"), country("SG", "新加坡"), country("MY", "马来西亚"),
            country("TH", "泰国"), country("VN", "越南"), country("ID", "印度尼西亚"), country("PH", "菲律宾"),
            country("IN", "印度"), country("PK", "巴基斯坦"), country("BD", "孟加拉国"), country("LK", "斯里兰卡"),
            country("KZ", "哈萨克斯坦"), country("UZ", "乌兹别克斯坦"), country("RU", "俄罗斯"), country("US", "美国"),
            country("CA", "加拿大"), country("GB", "英国"), country("FR", "法国"), country("DE", "德国"),
            country("IT", "意大利"), country("ES", "西班牙"), country("NL", "荷兰"), country("CH", "瑞士"),
            country("AU", "澳大利亚"), country("NZ", "新西兰"), country("ZA", "南非"), country("EG", "埃及")
    );

    @GetMapping("/students/template")
    public ResponseEntity<byte[]> studentTemplate(@RequestParam(defaultValue = "xlsx") String format) {
        SecurityUsers.requireAdmin();
        return template("学生导入模板", STUDENT_HEADERS, STUDENT_EXAMPLE,
                STUDENT_INSTRUCTIONS, COUNTRY_DICTIONARY, format);
    }

    @GetMapping("/rooms/template")
    public ResponseEntity<byte[]> roomTemplate(@RequestParam(defaultValue = "xlsx") String format) {
        SecurityUsers.requireAdmin();
        return template("宿舍导入模板", ROOM_HEADERS, ROOM_EXAMPLE,
                ROOM_INSTRUCTIONS, List.of(), format);
    }

    private ResponseEntity<byte[]> template(
            String name,
            List<String> headers,
            List<String> example,
            List<String> instructions,
            List<List<String>> dictionaryRows,
            String format) {
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] content = csv
                ? SpreadsheetSupport.csvTemplate(headers, example, instructions)
                : SpreadsheetSupport.xlsxTemplate(name, headers, example, instructions, dictionaryRows);
        String filename = java.net.URLEncoder.encode(
                name + (csv ? ".csv" : ".xlsx"), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(csv
                        ? "text/csv;charset=UTF-8"
                        : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + filename)
                .body(content);
    }

    private static List<String> country(String code, String name) {
        return List.of(code, name, PhoneNumberNormalizer.dialCode(code));
    }
}
