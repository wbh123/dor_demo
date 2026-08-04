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
            "本模板是当前系统唯一支持的学生导入模板，表头必须保持原顺序和原文字。",
            "必填字段：学号、姓名、性别、专业编码、国家/地区代码、学生类别；培养层次、年级和手机号码可留空。",
            "请在“数据填写”工作表中录入数据；“国家地区代码”和“字段枚举”工作表仅供查询，不要删除。",
            "国家或地区允许中文名称、英文名称或两位代码，例如中国大陆、China、CN，系统会统一保存为两位代码。",
            "国内生可填写中国大陆、中国香港、中国澳门或中国台湾；国际生必须填写可识别的国家或地区。",
            "手机号可填写带地区码的国际格式，也可填写本地号码；系统会根据国家或地区补全地区码，并自动去除空格、短横线和括号。",
            "年级支持2026或2026级；性别、学生类别和培养层次支持字段枚举表中的中文或规范代码。",
            "预检不会写入正式数据。所有行通过预检并由管理员确认后才会提交；无法唯一推断的内容会按行返回错误，不会猜测写入。",
            "同一文件内学号重复、学号已存在、专业停用、手机号不合法等情况均会在预检中提示。"
    );
    private static final List<List<String>> STUDENT_ENUMS = List.of(
            enumeration("学号", "12位数字", "12位数字", "去除首尾空格，不补位、不截断"),
            enumeration("姓名", "真实姓名", "原始文字", "去除首尾及全角空格，不允许空值"),
            enumeration("性别", "男、女、M、F", "M或F", "忽略英文大小写"),
            enumeration("专业编码", "系统内已启用专业编码", "大写专业编码", "忽略大小写并去除首尾空格"),
            enumeration("国家/地区代码", "中文名、英文名、ISO两位代码", "ISO两位代码", "例如中国大陆、China、CN均识别为CN"),
            enumeration("学生类别", "国内生、国际生、DOMESTIC、INTERNATIONAL", "DOMESTIC或INTERNATIONAL", "忽略英文大小写"),
            enumeration("培养层次", "本科生、硕士生、博士生、硕博生及对应英文代码", "UNDERGRADUATE、MASTER、DOCTOR、MASTER_DOCTOR", "可留空，忽略英文大小写"),
            enumeration("年级", "四位年份或四位年份+级", "四位年份", "例如2026级规范为2026"),
            enumeration("手机号码（含国家码）", "国际格式或本地号码", "+地区码+号码", "去除空格、短横线、括号；本地号码按国籍补地区码")
    );

    private static final List<String> ROOM_HEADERS = List.of(
            "楼栋编码", "楼栋名称", "楼层", "房间号", "房型", "容量",
            "性别", "学生类别", "运行状态", "备注");
    private static final List<String> ROOM_EXAMPLE = List.of(
            "A", "示例一号楼", "1", "101", "FIVE_PERSON", "5",
            "F", "MIXED", "ENABLED", "示例数据，请按实际情况修改");
    private static final List<String> ROOM_INSTRUCTIONS = List.of(
            "本模板是当前系统唯一支持的宿舍导入模板，表头必须保持原顺序和原文字。",
            "必填字段：楼栋编码、楼栋名称、楼层、房间号、房型、容量、性别、学生类别、运行状态；备注可留空。",
            "楼栋编码、楼层和房间号共同确定一间宿舍；重复房间、重复床位或容量冲突会在预检中提示。",
            "房型、性别、学生类别和运行状态必须使用“字段枚举”工作表允许的中文或规范代码。",
            "容量必须是正整数；楼层支持1或1层，系统统一保存为整数。",
            "预检不会写入正式数据。确认提交前可下载错误明细并修正；无法唯一推断的内容不会猜测写入。",
            "模板导入只维护楼栋和房间基础资料，具体床位布局仍由宿舍布局编辑器确认。"
    );
    private static final List<List<String>> ROOM_ENUMS = List.of(
            enumeration("楼栋编码", "字母、数字或短横线", "系统楼栋编码", "去除首尾空格并转大写"),
            enumeration("楼栋名称", "完整楼栋名称", "原始文字", "去除首尾及全角空格"),
            enumeration("楼层", "整数或整数+层", "整数", "例如3层规范为3"),
            enumeration("房间号", "楼内唯一房间号", "原始编号", "保留前导零，去除首尾空格"),
            enumeration("房型", "四人间、五人间、六人间、其他或规范代码", "FOUR_PERSON、FIVE_PERSON、SIX_PERSON、OTHER", "忽略英文大小写"),
            enumeration("容量", "正整数", "正整数", "不得小于1"),
            enumeration("性别", "男、女、M、F", "M或F", "忽略英文大小写"),
            enumeration("学生类别", "国内生宿舍、国际生宿舍、混住宿舍或规范代码", "DOMESTIC_ONLY、INTERNATIONAL_ONLY、MIXED", "忽略英文大小写"),
            enumeration("运行状态", "启用、停用、维护或规范代码", "ENABLED、DISABLED、MAINTENANCE", "忽略英文大小写"),
            enumeration("备注", "最多500个字符", "原始文字", "可留空")
    );

    private static final List<List<String>> COUNTRY_DICTIONARY = List.of(
            country("CN", "中国大陆", "China"), country("HK", "中国香港", "Hong Kong"),
            country("MO", "中国澳门", "Macao"), country("TW", "中国台湾", "Taiwan"),
            country("JP", "日本", "Japan"), country("KR", "韩国", "South Korea"),
            country("SG", "新加坡", "Singapore"), country("MY", "马来西亚", "Malaysia"),
            country("TH", "泰国", "Thailand"), country("VN", "越南", "Vietnam"),
            country("ID", "印度尼西亚", "Indonesia"), country("PH", "菲律宾", "Philippines"),
            country("IN", "印度", "India"), country("PK", "巴基斯坦", "Pakistan"),
            country("BD", "孟加拉国", "Bangladesh"), country("LK", "斯里兰卡", "Sri Lanka"),
            country("KZ", "哈萨克斯坦", "Kazakhstan"), country("UZ", "乌兹别克斯坦", "Uzbekistan"),
            country("RU", "俄罗斯", "Russia"), country("US", "美国", "United States"),
            country("CA", "加拿大", "Canada"), country("GB", "英国", "United Kingdom"),
            country("FR", "法国", "France"), country("DE", "德国", "Germany"),
            country("IT", "意大利", "Italy"), country("ES", "西班牙", "Spain"),
            country("NL", "荷兰", "Netherlands"), country("CH", "瑞士", "Switzerland"),
            country("AU", "澳大利亚", "Australia"), country("NZ", "新西兰", "New Zealand"),
            country("ZA", "南非", "South Africa"), country("EG", "埃及", "Egypt"),
            country("AE", "阿联酋", "United Arab Emirates"), country("SA", "沙特阿拉伯", "Saudi Arabia"),
            country("BR", "巴西", "Brazil"), country("MX", "墨西哥", "Mexico")
    );

    @GetMapping("/students/template")
    public ResponseEntity<byte[]> studentTemplate(
            @RequestParam(defaultValue = "xlsx") String format) {
        SecurityUsers.requireAdmin();
        return template(
                "学生导入模板",
                STUDENT_HEADERS,
                STUDENT_EXAMPLE,
                STUDENT_INSTRUCTIONS,
                COUNTRY_DICTIONARY,
                STUDENT_ENUMS,
                format);
    }

    @GetMapping("/rooms/template")
    public ResponseEntity<byte[]> roomTemplate(
            @RequestParam(defaultValue = "xlsx") String format) {
        SecurityUsers.requireAdmin();
        return template(
                "宿舍导入模板",
                ROOM_HEADERS,
                ROOM_EXAMPLE,
                ROOM_INSTRUCTIONS,
                List.of(),
                ROOM_ENUMS,
                format);
    }

    private ResponseEntity<byte[]> template(
            String name,
            List<String> headers,
            List<String> example,
            List<String> instructions,
            List<List<String>> dictionaryRows,
            List<List<String>> enumerationRows,
            String format) {
        boolean csv = "csv".equalsIgnoreCase(format);
        List<String> csvInstructions = java.util.stream.Stream.concat(
                instructions.stream(),
                enumerationRows.stream().map(row -> "字段枚举：" + row.get(0) + " = " + row.get(1)))
                .toList();
        byte[] content = csv
                ? SpreadsheetSupport.csvTemplate(headers, example, csvInstructions)
                : SpreadsheetSupport.xlsxTemplate(
                        name,
                        headers,
                        example,
                        instructions,
                        dictionaryRows,
                        enumerationRows);
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

    private static List<String> country(String code, String chinese, String english) {
        return List.of(code, chinese, english, PhoneNumberNormalizer.dialCode(code));
    }

    private static List<String> enumeration(
            String field,
            String accepted,
            String normalized,
            String tolerance) {
        return List.of(field, accepted, normalized, tolerance);
    }
}
