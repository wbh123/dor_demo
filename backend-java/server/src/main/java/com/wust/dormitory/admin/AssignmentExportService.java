package com.wust.dormitory.admin;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class AssignmentExportService {
    private final NamedParameterJdbcTemplate jdbc;

    public AssignmentExportService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Resource exportCsv(long batchId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT s.student_number, s.student_name, s.gender,
                       m.major_code, b.building_name, r.room_number,
                       bed.bed_code, a.assignment_method, a.assigned_at
                FROM bed_assignment a
                JOIN student s ON s.id=a.student_id
                JOIN major m ON m.id=s.major_id
                JOIN bed ON bed.id=a.bed_id
                JOIN room r ON r.id=bed.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building b ON b.id=f.building_id
                WHERE a.batch_id=:batchId
                ORDER BY s.student_number
                """, Map.of("batchId", batchId));
        StringBuilder csv = new StringBuilder("学号,姓名,性别,专业编号,楼栋,房间,床位,分配方式,分配时间\n");
        for (Map<String, Object> row : rows) {
            append(csv, row.get("student_number"));
            append(csv, row.get("student_name"));
            append(csv, row.get("gender"));
            append(csv, row.get("major_code"));
            append(csv, row.get("building_name"));
            append(csv, row.get("room_number"));
            append(csv, row.get("bed_code"));
            append(csv, row.get("assignment_method"));
            appendLast(csv, row.get("assigned_at"));
        }
        return new ByteArrayResource(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8));
    }

    private void append(StringBuilder csv, Object value) {
        csv.append(escape(value)).append(',');
    }

    private void appendLast(StringBuilder csv, Object value) {
        csv.append(escape(value)).append('\n');
    }

    private String escape(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
