#!/usr/bin/env python3
from pathlib import Path
import re

path = Path("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java")
text = path.read_text(encoding="utf-8")

import_anchor = "import com.wust.dormitory.admin.mapper.AdminCatalogMapper;"
import_replacement = """import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.mapper.StudentAdminMapper;"""
if import_anchor not in text:
    raise RuntimeError("AdminService mapper import anchor not found")
text = text.replace(import_anchor, import_replacement, 1)

row_anchor = "import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;"
row_replacement = """import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;
import com.wust.dormitory.admin.model.persistence.StudentCatalogRow;
import com.wust.dormitory.admin.model.query.StudentCatalogQuery;"""
if row_anchor not in text:
    raise RuntimeError("AdminService row import anchor not found")
text = text.replace(row_anchor, row_replacement, 1)

constructor_old = """    private final AuditService auditService;
    private final AdminCatalogMapper adminCatalogMapper;

    public AdminService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AuditService auditService,
            AdminCatalogMapper adminCatalogMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.adminCatalogMapper = adminCatalogMapper;
    }
"""
constructor_new = """    private final AuditService auditService;
    private final AdminCatalogMapper adminCatalogMapper;
    private final StudentAdminMapper studentAdminMapper;

    public AdminService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AuditService auditService,
            AdminCatalogMapper adminCatalogMapper,
            StudentAdminMapper studentAdminMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.adminCatalogMapper = adminCatalogMapper;
        this.studentAdminMapper = studentAdminMapper;
    }
"""
if constructor_old not in text:
    raise RuntimeError("AdminService constructor anchor not found")
text = text.replace(constructor_old, constructor_new, 1)

student_pattern = re.compile(
    r"    public Map<String, Object> students\(String keyword, String gender, Long majorId, int page, int size\) \{.*?\n    \}\n\n    @Transactional\n    public long saveStudent",
    re.DOTALL,
)
student_replacement = """    public Map<String, Object> students(
            String keyword,
            String gender,
            Long majorId,
            int page,
            int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 200);
        String keywordPattern = keyword == null || keyword.isBlank()
                ? null
                : "%" + keyword.trim() + "%";
        String genderFilter = gender == null || gender.isBlank() ? null : gender;
        StudentCatalogQuery query = new StudentCatalogQuery(
                keywordPattern,
                genderFilter,
                majorId,
                safeSize,
                (safePage - 1) * safeSize);
        int total = Math.toIntExact(studentAdminMapper.countStudents(query));
        List<Map<String, Object>> items = studentAdminMapper.findStudents(query).stream()
                .map(StudentCatalogRow::asResponseMap)
                .toList();
        return Map.of(
                "page", safePage,
                "size", safeSize,
                "total", total,
                "items", items);
    }

    @Transactional
    public long saveStudent"""
text, student_count = student_pattern.subn(student_replacement, text, count=1)
if student_count != 1:
    raise RuntimeError(f"expected one students method replacement, got {student_count}")

rooms_pattern = re.compile(
    r"\n    public List<Map<String, Object>> rooms\(Long buildingId, String gender\) \{.*?"
    r"\n    \}\n\n    @Transactional\n    public void updateRoom",
    re.DOTALL,
)
text, rooms_count = rooms_pattern.subn(
    "\n\n    @Transactional\n    public void updateRoom",
    text,
    count=1,
)
if rooms_count != 1:
    raise RuntimeError(f"expected one obsolete rooms method removal, got {rooms_count}")

students_start = text.index("public Map<String, Object> students")
students_end = text.index("@Transactional", students_start)
students_body = text[students_start:students_end]
if "jdbc." in students_body or "SELECT " in students_body:
    raise RuntimeError("students method still contains JDBC or SQL")
if "public List<Map<String, Object>> rooms(" in text:
    raise RuntimeError("obsolete AdminService.rooms method still exists")

path.write_text(text, encoding="utf-8")
print("AdminService student catalog query migrated to MyBatis")
