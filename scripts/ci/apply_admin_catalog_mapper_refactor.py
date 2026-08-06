#!/usr/bin/env python3
# Temporary branch-only migration helper; removed after successful execution.
from pathlib import Path
import re

path = Path("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java")
text = path.read_text(encoding="utf-8")

import_anchor = "import com.wust.dormitory.audit.AuditService;"
import_replacement = """import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;
import com.wust.dormitory.admin.model.persistence.MajorCatalogRow;
import com.wust.dormitory.audit.AuditService;"""
if import_anchor not in text:
    raise RuntimeError("AdminService import anchor not found")
text = text.replace(import_anchor, import_replacement, 1)

constructor_old = """    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public AdminService(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper,
                        AuditService auditService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }
"""
constructor_new = """    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
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
if constructor_old not in text:
    raise RuntimeError("AdminService constructor anchor not found")
text = text.replace(constructor_old, constructor_new, 1)

major_pattern = re.compile(
    r"    public List<Map<String, Object>> majors\(Boolean enabled\) \{.*?\n    \}\n\n    @Transactional",
    re.DOTALL,
)
major_replacement = """    public List<Map<String, Object>> majors(Boolean enabled) {
        return adminCatalogMapper.findMajors(enabled).stream()
                .map(MajorCatalogRow::asResponseMap)
                .toList();
    }

    @Transactional"""
text, major_count = major_pattern.subn(major_replacement, text, count=1)
if major_count != 1:
    raise RuntimeError(f"expected one majors method replacement, got {major_count}")

building_pattern = re.compile(
    r"    public List<Map<String, Object>> buildings\(\) \{.*?\n    \}\n\n    public List<Map<String, Object>> rooms",
    re.DOTALL,
)
building_replacement = """    public List<Map<String, Object>> buildings() {
        return adminCatalogMapper.findBuildings().stream()
                .map(BuildingCatalogRow::asResponseMap)
                .toList();
    }

    public List<Map<String, Object>> rooms"""
text, building_count = building_pattern.subn(building_replacement, text, count=1)
if building_count != 1:
    raise RuntimeError(f"expected one buildings method replacement, got {building_count}")

for method_name in ("majors", "buildings"):
    start = text.index(f"public List<Map<String, Object>> {method_name}")
    next_method = text.find("\n    public ", start + 10)
    next_transaction = text.find("\n    @Transactional", start + 10)
    candidates = [value for value in (next_method, next_transaction) if value >= 0]
    end = min(candidates)
    body = text[start:end]
    if "jdbc." in body or "SELECT " in body:
        raise RuntimeError(f"{method_name} method still contains JDBC or SQL")

path.write_text(text, encoding="utf-8")
print("AdminService major and building catalogs migrated to MyBatis")
