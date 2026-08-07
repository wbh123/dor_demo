package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SpreadsheetSupport {
    private static final String DATA_SHEET_NAME = "数据填写";

    private SpreadsheetSupport() {
    }

    public static List<Map<String, String>> read(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("IMPORT_FILE_REQUIRED", "请选择Excel或CSV文件");
        }
        String name = file.getOriginalFilename() == null
                ? ""
                : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) {
                return SpreadsheetTextSupport.readCsv(
                        new String(file.getBytes(), StandardCharsets.UTF_8));
            }
            if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                    return readSheet(findDataSheet(workbook));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException("IMPORT_FILE_INVALID", "文件读取失败，请检查文件是否完整");
        }
        throw new BusinessException("IMPORT_FILE_TYPE_INVALID", "仅支持.xlsx、.xls或.csv文件");
    }

    public static byte[] csvTemplate(List<String> headers, List<String> example) {
        return csvTemplate(headers, example, List.of());
    }

    public static byte[] csvTemplate(
            List<String> headers,
            List<String> example,
            List<String> instructions) {
        StringBuilder text = new StringBuilder("\uFEFF");
        for (String instruction : instructions) {
            text.append(SpreadsheetTextSupport.csvLine(List.of("# " + instruction)))
                    .append('\n');
        }
        text.append(SpreadsheetTextSupport.csvLine(headers)).append('\n');
        text.append(SpreadsheetTextSupport.csvLine(example)).append('\n');
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] xlsxTemplate(
            String sheetName,
            List<String> headers,
            List<String> example) {
        return xlsxTemplate(sheetName, headers, example, List.of(), List.of(), List.of());
    }

    public static byte[] xlsxTemplate(
            String templateName,
            List<String> headers,
            List<String> example,
            List<String> instructions,
            List<List<String>> dictionaryRows) {
        return xlsxTemplate(
                templateName,
                headers,
                example,
                instructions,
                dictionaryRows,
                List.of());
    }

    public static byte[] xlsxTemplate(
            String templateName,
            List<String> headers,
            List<String> example,
            List<String> instructions,
            List<List<String>> dictionaryRows,
            List<List<String>> enumerationRows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeInstructionSheet(workbook, templateName, instructions);
            writeDataSheet(workbook, headers, example);
            if (!dictionaryRows.isEmpty()) writeDictionarySheet(workbook, dictionaryRows);
            if (!enumerationRows.isEmpty()) writeEnumerationSheet(workbook, enumerationRows);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("模板生成失败", exception);
        }
    }

    private static Sheet findDataSheet(Workbook workbook) {
        Sheet named = workbook.getSheet(DATA_SHEET_NAME);
        if (named != null) return named;
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        for (Sheet sheet : workbook) {
            for (Row row : sheet) {
                String joined = rowValues(row, formatter).stream()
                        .map(SpreadsheetTextSupport::normalizeHeader)
                        .reduce((left, right) -> left + "," + right)
                        .orElse("");
                if (joined.contains("学号")
                        || joined.contains("楼栋编码")
                        || joined.contains("studentnumber")) {
                    return sheet;
                }
            }
        }
        return workbook.getSheetAt(0);
    }

    private static List<Map<String, String>> readSheet(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        List<String> headers = null;
        List<Map<String, String>> rows = new ArrayList<>();
        for (Row row : sheet) {
            List<String> values = rowValues(row, formatter);
            if (values.stream().allMatch(String::isBlank)) continue;
            if (values.getFirst().startsWith("#")) continue;
            if (headers == null) {
                headers = values.stream()
                        .map(SpreadsheetTextSupport::normalizeHeader)
                        .toList();
                continue;
            }
            rows.add(SpreadsheetTextSupport.map(headers, values));
        }
        return rows;
    }

    private static List<String> rowValues(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        int cells = Math.max(row.getLastCellNum(), 0);
        for (int index = 0; index < cells; index++) {
            Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
        }
        return values;
    }

    private static void writeInstructionSheet(
            Workbook workbook,
            String templateName,
            List<String> instructions) {
        Sheet sheet = workbook.createSheet("填写说明");
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);
        Row title = sheet.createRow(0);
        title.createCell(0).setCellValue(templateName + "填写说明");
        title.getCell(0).setCellStyle(titleStyle);
        int rowIndex = 2;
        List<String> content = instructions.isEmpty()
                ? List.of("请在“数据填写”工作表中录入数据，不要修改表头。")
                : instructions;
        for (int index = 0; index < content.size(); index++) {
            Row row = sheet.createRow(rowIndex++);
            row.createCell(0).setCellValue((index + 1) + ". " + content.get(index));
        }
        sheet.setColumnWidth(0, 30000);
    }

    private static void writeDataSheet(
            Workbook workbook,
            List<String> headers,
            List<String> example) {
        Sheet sheet = workbook.createSheet(DATA_SHEET_NAME);
        CellStyle headerStyle = coloredHeaderStyle(workbook, IndexedColors.DARK_BLUE);
        Row headerRow = sheet.createRow(0);
        writeRow(headerRow, headers);
        for (Cell cell : headerRow) cell.setCellStyle(headerStyle);
        writeRow(sheet.createRow(1), example);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                0, 0, 0, Math.max(0, headers.size() - 1)));
        autoSize(sheet, headers.size(), 16000);
    }

    private static void writeDictionarySheet(
            Workbook workbook,
            List<List<String>> dictionaryRows) {
        Sheet sheet = workbook.createSheet("国家地区代码");
        Row header = sheet.createRow(0);
        writeRow(header, List.of("国家/地区代码", "中文名称", "英文名称", "手机地区码"));
        CellStyle style = coloredHeaderStyle(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE);
        for (Cell cell : header) cell.setCellStyle(style);
        for (int index = 0; index < dictionaryRows.size(); index++) {
            writeRow(sheet.createRow(index + 1), dictionaryRows.get(index));
        }
        sheet.createFreezePane(0, 1);
        autoSize(sheet, 4, 14000);
    }

    private static void writeEnumerationSheet(
            Workbook workbook,
            List<List<String>> enumerationRows) {
        Sheet sheet = workbook.createSheet("字段枚举");
        Row header = sheet.createRow(0);
        writeRow(header, List.of("字段名称", "允许填写内容", "系统规范值", "容错说明"));
        CellStyle style = coloredHeaderStyle(workbook, IndexedColors.LIGHT_CORNFLOWER_BLUE);
        for (Cell cell : header) cell.setCellStyle(style);
        for (int index = 0; index < enumerationRows.size(); index++) {
            writeRow(sheet.createRow(index + 1), enumerationRows.get(index));
        }
        sheet.createFreezePane(0, 1);
        autoSize(sheet, 4, 22000);
    }

    private static CellStyle coloredHeaderStyle(
            Workbook workbook,
            IndexedColors color) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        if (color == IndexedColors.DARK_BLUE) font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static void autoSize(Sheet sheet, int columns, int maximumWidth) {
        for (int index = 0; index < columns; index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index,
                    Math.min(sheet.getColumnWidth(index) + 1400, maximumWidth));
        }
    }

    private static void writeRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            row.createCell(index).setCellValue(values.get(index));
        }
    }
}
