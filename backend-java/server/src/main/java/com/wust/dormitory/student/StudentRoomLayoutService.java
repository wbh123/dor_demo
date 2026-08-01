package com.wust.dormitory.student;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentRoomLayoutService {
    private final NamedParameterJdbcTemplate jdbc;

    public StudentRoomLayoutService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> enrich(Map<String, Object> snapshot) {
        Map<String, Object> room = (Map<String, Object>) snapshot.get("room");
        List<Map<String, Object>> originalBeds =
                (List<Map<String, Object>>) snapshot.getOrDefault("beds", List.of());
        if (room == null || originalBeds.isEmpty()) {
            return snapshot;
        }

        long roomId = ((Number) room.get("id")).longValue();
        List<Map<String, Object>> layoutRows = jdbc.queryForList("""
                SELECT bed.id AS bed_id,
                       layout.layout_x,
                       layout.layout_z,
                       layout.rotation_degrees
                FROM bed
                LEFT JOIN room_bed_layout layout ON layout.bed_id=bed.id
                WHERE bed.room_id=:roomId
                """, Map.of("roomId", roomId));
        Map<Long, Map<String, Object>> layoutByBed = new HashMap<>();
        for (Map<String, Object> row : layoutRows) {
            layoutByBed.put(((Number) row.get("bed_id")).longValue(), row);
        }

        boolean hasCustom = false;
        List<Map<String, Object>> beds = new ArrayList<>(originalBeds.size());
        for (Map<String, Object> original : originalBeds) {
            Map<String, Object> bed = new LinkedHashMap<>(original);
            long bedId = ((Number) original.get("id")).longValue();
            Map<String, Object> layout = layoutByBed.get(bedId);
            if (layout != null && layout.get("layout_x") != null) {
                bed.put("layout_x", layout.get("layout_x"));
                bed.put("layout_z", layout.get("layout_z"));
                bed.put("rotation_degrees", layout.get("rotation_degrees"));
                bed.put("custom_layout", true);
                hasCustom = true;
            } else {
                bed.put("custom_layout", false);
            }
            beds.add(bed);
        }

        Map<String, Object> enriched = new LinkedHashMap<>(snapshot);
        enriched.put("beds", beds);
        enriched.put("layout_source", hasCustom ? "CUSTOM_LAYOUT" : "DEFAULT_LAYOUT");
        return enriched;
    }
}
