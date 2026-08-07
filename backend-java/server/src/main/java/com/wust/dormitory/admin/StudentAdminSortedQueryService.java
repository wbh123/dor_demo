package com.wust.dormitory.admin;

import com.wust.dormitory.admin.mapper.StudentAdminSortedMapper;
import com.wust.dormitory.admin.model.persistence.StudentAdminDetailRow;
import com.wust.dormitory.admin.model.query.StudentAdminSortedQuery;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class StudentAdminSortedQueryService {
    private static final Set<String> SORT_FIELDS = Set.of(
            "studentNumber", "studentName", "gender", "majorName",
            "accountStatus", "gradeYear", "studentCategory", "degreeLevel");

    private final StudentAdminSortedMapper mapper;

    public StudentAdminSortedQueryService(StudentAdminSortedMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, Object> query(
            String keyword,
            String gender,
            Long majorId,
            String studentCategory,
            String enrollmentSource,
            int page,
            int size,
            String sortField,
            String sortDirection) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.max(1, Math.min(100, size));
        String normalizedField = SORT_FIELDS.contains(sortField) ? sortField : "studentNumber";
        String normalizedDirection = "desc".equalsIgnoreCase(sortDirection) ? "desc" : "asc";
        StudentAdminSortedQuery query = new StudentAdminSortedQuery(
                like(keyword), clean(gender), majorId, clean(studentCategory), clean(enrollmentSource),
                normalizedField, normalizedDirection, normalizedSize, (normalizedPage - 1) * normalizedSize);
        long total = mapper.countStudents(query);
        List<Map<String, Object>> items = mapper.findStudents(query).stream()
                .map(StudentAdminDetailRow::asResponseMap)
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", items);
        response.put("page", normalizedPage);
        response.put("size", normalizedSize);
        response.put("total", total);
        response.put("sortField", normalizedField);
        response.put("sortDirection", normalizedDirection);
        return response;
    }

    private String clean(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String like(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String escaped = value.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
