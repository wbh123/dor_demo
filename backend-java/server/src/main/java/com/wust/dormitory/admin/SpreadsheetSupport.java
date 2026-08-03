package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
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
                    return readSheet(workbook.getSheetAt(0));
                }
            }
        } catch (IOException exception) {
            throw new BusinessException("IMPORT_FILE_INVALID", "文件读取失败，请检查文件是否完整");
        }
        throw new BusinessException("IMPORT_FILE_TYPE_INVALID", "仅支持.xlsx、.xls或.csv文件");
    }

    public static byte[] csvTemplate(List<String> headers, List<String> example) {
        StringBuilder text = new StringBuilder("\uFEFF");
        text.append(csvLine(headers)).append('\n');
        text.append(csvLine(example)).append('\n');
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] xlsxTemplate(String sheetName, List<String> headers, List<String> example) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeRow(sheet.createRow(0), headers);
            writeRow(sheet.createRow(1), example);
            for (int index = 0; index < headers.size(); index++) {
                sheet.autoSizeColumn(index);
                sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 1024, 12000));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("模板生成失败", exception);
        }
    }

    private static List<Map<String, String>> readSheet(Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        List<String> headers = null;
        List<Map<String, String>> rows = new ArrayList<>();
        for (Row row : sheet) {
            List<String> values = new ArrayList<>();
            int cells = Math.max(row.getLastCellNum(), 0);
            for (int index = 0; index < cells; index++) {
                Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                values.add(cell == null ? "" : formatter.formatCellValue(cell).trim());
            }
            if (headers == null) {
                headers = values.stream().map(SpreadsheetSupport::normalizeHeader).toList();
                continue;
            }
            if (values.stream().allMatch(String::isBlank)) continue;
            rows.add(map(headers, values));
        }
        return rows;
    }

    private static List<Map<String, String>> readCsv(String source) {
        List<List<String>> parsed = parseCsv(source.replace("\uFEFF", ""));
        if (parsed.isEmpty()) return List.of();
        List<String> headers = parsed.getFirst().stream().map(SpreadsheetSupport::normalizeHeader).toList();
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < parsed.size(); index++) {
            List<String> values = parsed.get(index);
            if (values.stream().allMatch(String::isBlank)) continue;
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
        return value.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
    }

    private static void writeRow(Row row, List<String> values) {
        for (int index = 0; index < values.size(); index++) row.createCell(index).setCellValue(values.get(index));
    }

    private static String csvLine(List<String> values) {
        return values.stream().map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .reduce((left, right) -> left + "," + right).orElse("");
    }
}
