package com.wust.dormitory.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class SpreadsheetTextSupport {
    private SpreadsheetTextSupport() {
    }

    static List<Map<String, String>> readCsv(String source) {
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
        List<String> headers = parsed.get(headerIndex).stream()
                .map(SpreadsheetTextSupport::normalizeHeader)
                .toList();
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
                if (current == '"'
                        && index + 1 < source.length()
                        && source.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else if (current == '"') {
                    quoted = false;
                } else {
                    cell.append(current);
                }
            } else if (current == '"') {
                quoted = true;
            } else if (current == ',') {
                row.add(cell.toString().trim());
                cell.setLength(0);
            } else if (current == '\n') {
                row.add(cell.toString().trim());
                cell.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else if (current != '\r') {
                cell.append(current);
            }
        }
        row.add(cell.toString().trim());
        if (row.size() > 1 || !row.getFirst().isBlank()) rows.add(row);
        return rows;
    }

    static Map<String, String> map(List<String> headers, List<String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < headers.size(); index++) {
            result.put(headers.get(index), index < values.size() ? values.get(index).trim() : "");
        }
        return result;
    }

    static String normalizeHeader(String value) {
        return value.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("(", "（")
                .replace(")", "）");
    }

    static String csvLine(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\"", "\"\"") + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
