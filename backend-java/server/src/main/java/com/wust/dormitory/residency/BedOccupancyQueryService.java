package com.wust.dormitory.residency;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BedOccupancyQueryService {
    private final BedOccupancyMapper mapper;

    public BedOccupancyQueryService(BedOccupancyMapper mapper) {
        this.mapper = mapper;
    }

    public Map<Long, BedOccupancy> describeRoom(long roomId) {
        Map<Long, BedOccupancy> result = new LinkedHashMap<>();
        for (Map<String, Object> row : mapper.findRoomBedOccupancy(roomId)) {
            long bedId = number(row.get("bed_id"));
            String occupancySource = text(row.get("occupancy_source"), "AVAILABLE");
            Long studentId = nullableNumber(row.get("occupant_student_id"));
            result.put(bedId, new BedOccupancy(
                    bedId,
                    Boolean.TRUE.equals(booleanValue(row.get("occupied"))),
                    occupancySource,
                    studentId,
                    text(row.get("occupant_student_number"), null),
                    text(row.get("occupant_student_name"), null),
                    text(row.get("blocking_reason"), null)));
        }
        return result;
    }

    public List<Map<String, Object>> describeRoomAsList(long roomId) {
        return describeRoom(roomId).values().stream()
                .map(BedOccupancy::asResponseMap)
                .toList();
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private Long nullableNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.intValue() != 0;
        return value == null ? null : Boolean.parseBoolean(String.valueOf(value));
    }

    private String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    public record BedOccupancy(
            long bedId,
            boolean occupied,
            String occupancySource,
            Long occupantStudentId,
            String occupantStudentNumber,
            String occupantStudentName,
            String blockingReason) {
        public Map<String, Object> asResponseMap() {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("bedId", bedId);
            response.put("occupied", occupied);
            response.put("occupancySource", occupancySource);
            response.put("occupantStudentId", occupantStudentId);
            response.put("occupantStudentNumber", occupantStudentNumber);
            response.put("occupantStudentName", occupantStudentName);
            response.put("blockingReason", blockingReason);
            return response;
        }
    }
}
