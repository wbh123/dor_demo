package com.wust.dormitory.admin;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpreadsheetSupportTest {
    @Test
    void readsQuotedCsvAndNormalizesChineseHeaders() {
        String csv = "\uFEFF\"学号\",\"姓名\",\"国家/地区\"\n"
                + "\"202600000001\",\"张,三\",\"中国\"\n";
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));

        List<Map<String, String>> rows = SpreadsheetSupport.read(file);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst())
                .containsEntry("学号", "202600000001")
                .containsEntry("姓名", "张,三")
                .containsEntry("国家/地区", "中国");
    }

    @Test
    void generatedXlsxTemplateCanBeReadBack() {
        byte[] content = SpreadsheetSupport.xlsxTemplate(
                "学生导入模板",
                List.of("学号", "国家/地区"),
                List.of("202600000001", "中国"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content);

        assertThat(SpreadsheetSupport.read(file).getFirst())
                .containsEntry("学号", "202600000001")
                .containsEntry("国家/地区", "中国");
    }
}
