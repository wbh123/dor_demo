package com.wust.dormitory.importworkflow;

import com.wust.dormitory.common.error.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StrictImportHeaders {
    public static final List<String> STUDENT_HEADERS = List.of(
            "学号", "姓名", "性别", "专业编码", "国家/地区代码", "学生类别",
            "培养层次", "年级", "手机号码（含国家码）");
    public static final List<String> ROOM_HEADERS = List.of(
            "楼栋编码", "楼栋名称", "楼层", "房间号", "房型", "容量",
            "性别", "学生类别", "运行状态", "备注");

    private StrictImportHeaders() { }

    public static void validate(String importType, List<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) return;
        List<String> expected = "STUDENT".equals(importType) ? STUDENT_HEADERS : ROOM_HEADERS;
        List<String> actual = new ArrayList<>(rows.getFirst().keySet());
        if (!expected.equals(actual)) {
            throw new BusinessException(
                    "IMPORT_HEADER_MISMATCH",
                    "导入表头必须与当前模板完全一致，且不支持旧模板。期望："
                            + String.join("、", expected));
        }
    }
}
