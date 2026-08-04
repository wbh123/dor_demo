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
import java.util.LinkedHashMap;
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
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) {
                return readCsv(new String(file.getBytes(), StandardCharsets.UTF_8));
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

    public static byte[] csvTemplate(List<String> headers, List<String> example, List<String> instructions) {
        StringBuilder text = new StringBuilder("\uFEFF");
        for (String instruction : instructions) {
            text.append(csvLine(List.of("# " + instruction))).append('\n');
        }
        text.append(csvLine(headers)).append('\n');
        text.append(csvLine(example)).append('\n');
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] xlsxTemplate(String sheetName, List<String> headers, List<String> example) {
        return xlsxTemplate(sheetName, headers, example, List.of(), List.of());
    }

    public static byte[] xlsxTemplate(
            String templateName,
            List<String> headers,
            List<String> example,
            List<String> instructions,
            List<List<String>> dictionaryRows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeInstructionSheet(workbook, templateName, instructions);
            writeDataSheet(workbook, headers, example);
            if (!dictionaryRows.isEmpty()) writeDictionarySheet(workbook, dictionaryRows);
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
                        .map(SpreadsheetSupport::normalizeHeader)
                        .reduce((left, right) -> left + "," + right)
                        .orElse("");
                if (joined.contains("学号") || joined.contains("楼栋编码") || joined.contains("studentnumber")) {
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
                headers = values.stream().map(SpreadsheetSupport::normalizeHeader).toList();
                continue;
            }
            rows.add(map(headers, values));
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

    private static List<Map<String, String>> readCsv(String source) {
        List<List<String>> parsed = parseCsv(source.replace("\uFEFF", ""));
        int headerIndex = -1;
        for (int index = 0; index < parsed.size(); index++) {
            List<String> values = parsed.get(index);
            if (values.stream().allMatch(String::isBlank)) continue;
            if (values.getFirst().trim().startsWith("#")) continue;
            headerIndex = index;
            break;
        }
        if (headerIndex < 0) return List.of();
        List<String> headers = parsed.get(headerIndex).stream().map(SpreadsheetSupport::normalizeHeader).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = headerIndex + 1; index < parsed.size(); index++) {
            List<String> values = parsed.get(index);
            if (values.stream().allMatch(String::isBlank)) continue;
            if (values.getFirst().trim().startsWith("#")) continue;
            rows.add(map(headers, values));
        }
        return rows;
    }

    private static List<List<String>> parseCsv(String source) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            if (quoted) {
                if (current == '"' && index + 1 < source.length() && source.charAt(index + 1) == '"') {
                    cell.append('"'); index++;
                } else if (current == '"') quoted = false;
                else cell.append(current);
            } else if (current == '"') quoted = true;
            else if (current == ',') { row.add(cell.toString().trim()); cell.setLength(0); }
            else if (current == '\n') { row.add(cell.toString().trim()); cell.setLength(0); rows.add(row); row = new ArrayList<>(); }
            else if (current != '\r') cell.append(current);
        }
        row.add(cell.toString().trim());
        if (row.size() > 1 || !row.getFirst().isBlank()) rows.add(row);
        return rows;
    }

    private static Map<String, String> map(List<String> headers, List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            result.put(headers.get(index), index < values.size() ? values.get(index).trim() : "");
        }
        return result;
    }

    private static String normalizeHeader(String value) {
        return value.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("(", "（")
                .replace(")", "）");
    }

    private static void writeInstructionSheet(Workbook workbook, String templateName, List<String> instructions) {
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
        sheet.setColumnWidth(0, 24000);
    }

    private static void writeDataSheet(Workbook workbook, List<String> headers, List<String> example) {
        Sheet sheet = workbook.createSheet(DATA_SHEET_NAME);
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        Row headerRow = sheet.createRow(0);
        writeRow(headerRow, headers);
        for (Cell cell : headerRow) cell.setCellStyle(headerStyle);
        writeRow(sheet.createRow(1), example);
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, Math.max(0, headers.size() - 1)));
        for (int index = 0; index < headers.size(); index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 1400, 16000));
        }
    }

    private static void writeDictionarySheet(Workbook workbook, List<List<String>> dictionaryRows) {
        Sheet sheet = workbook.createSheet("国家地区代码");
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Row header = sheet.createRow(0);
        writeRow(header, List.of("国家/地区代码", "国家或地区", "手机国家码"));
        for (Cell cell : header) cell.setCellStyle(headerStyle);
        for (int index = 0; index < dictionaryRows.size(); index++) {
            writeRow(sheet.createRow(index + 1), dictionaryRows.get(index));
        }
        sheet.createFreezePane(0, 1);
        for (int index = 0; index < 3; index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 1200, 12000));
        }
    }

    private static void writeRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) row.createCell(index).setCellValue(values.get(index));
    }

    private static String csvLine(List<String> values) {
        return values.stream().map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .reduce((left, right) -> left + "," + right).orElse("");
    }
}
