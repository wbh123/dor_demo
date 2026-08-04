package com.wust.dormitory.admin;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
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
    void ignoresCsvInstructionsBeforeHeader() {
        byte[] content = SpreadsheetSupport.csvTemplate(
                List.of("学号", "手机号码（含国家码）"),
                List.of("202600000001", "+8613800000000"),
                List.of("请勿修改表头", "手机号码必须包含国家码"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "students.csv", "text/csv", content);

        assertThat(SpreadsheetSupport.read(file).getFirst())
                .containsEntry("学号", "202600000001")
                .containsEntry("手机号码（含国家码）", "+8613800000000");
    }

    @Test
    void generatedXlsxTemplateContainsInstructionsDataAndDictionary() throws Exception {
        byte[] content = SpreadsheetSupport.xlsxTemplate(
                "学生导入模板",
                List.of("学号", "国家/地区代码"),
                List.of("202600000001", "CN"),
                List.of("请在数据填写工作表中录入"),
                List.of(List.of("CN", "中国大陆", "+86")));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            assertThat(workbook.getSheet("填写说明")).isNotNull();
            assertThat(workbook.getSheet("数据填写")).isNotNull();
            assertThat(workbook.getSheet("国家地区代码")).isNotNull();
            assertThat(workbook.getSheet("数据填写").getRow(0).getCell(0).getCellStyle().getFillPattern())
                    .isNotNull();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                content);
        assertThat(SpreadsheetSupport.read(file).getFirst())
                .containsEntry("学号", "202600000001")
                .containsEntry("国家/地区代码", "CN");
    }
}
