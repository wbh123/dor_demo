package com.wust.dormitory.residency;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CurrentResidencyQueryService {
    private final NamedParameterJdbcTemplate jdbc;

    public CurrentResidencyQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> current(long studentId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ra.id AS residency_id, ra.batch_id, ra.student_id,
                       ra.room_id, ra.bed_id, ra.team_id,
                       ra.source_selection_mode, ra.assignment_method,
                       ra.assigned_at, ra.bed_confirmed_at,
                       r.room_number, r.capacity, r.gender_restriction,
                       r.resident_scope, r.operational_status,
                       f.floor_number, db.id AS building_id,
                       db.building_name,
                       b.bed_code, b.bed_type,
                       (ra.bed_id IS NOT NULL) AS bed_confirmed
                FROM room_assignment ra
                JOIN room r ON r.id=ra.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                JOIN dormitory_building db ON db.id=f.building_id
                LEFT JOIN bed b ON b.id=ra.bed_id
                WHERE ra.student_id=:studentId AND ra.assignment_status='ACTIVE'
                ORDER BY ra.assigned_at DESC, ra.id DESC
                LIMIT 1
                """, Map.of("studentId", studentId));
        if (rows.isEmpty()) {
            return Map.of("resident", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resident", true);
        result.put("residency", rows.getFirst());
        return result;
    }

    public Map<String, Object> assignment(long studentId) {
        Map<String, Object> current = current(studentId);
        if (!Boolean.TRUE.equals(current.get("resident"))) {
            return Map.of("assigned", false);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assigned", true);
        result.put("assignment", current.get("residency"));
        return result;
    }
}
