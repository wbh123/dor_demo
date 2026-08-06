#!/usr/bin/env python3
from pathlib import Path
import re

path = Path("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java")
text = path.read_text(encoding="utf-8")

mapper_anchor = "import com.wust.dormitory.admin.mapper.AdminCatalogMapper;"
mapper_replacement = """import com.wust.dormitory.admin.mapper.AdminCatalogMapper;
import com.wust.dormitory.admin.mapper.AdminDashboardMapper;"""
if mapper_anchor not in text:
    raise RuntimeError("AdminService dashboard mapper import anchor not found")
text = text.replace(mapper_anchor, mapper_replacement, 1)

row_anchor = "import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;"
row_replacement = """import com.wust.dormitory.admin.model.persistence.AdminDashboardStatsRow;
import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;"""
if row_anchor not in text:
    raise RuntimeError("AdminService dashboard row import anchor not found")
text = text.replace(row_anchor, row_replacement, 1)

constructor_old = """    private final AdminCatalogMapper adminCatalogMapper;
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
constructor_new = """    private final AdminCatalogMapper adminCatalogMapper;
    private final StudentAdminMapper studentAdminMapper;
    private final AdminDashboardMapper adminDashboardMapper;

    public AdminService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AuditService auditService,
            AdminCatalogMapper adminCatalogMapper,
            StudentAdminMapper studentAdminMapper,
            AdminDashboardMapper adminDashboardMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.adminCatalogMapper = adminCatalogMapper;
        this.studentAdminMapper = studentAdminMapper;
        this.adminDashboardMapper = adminDashboardMapper;
    }
"""
if constructor_old not in text:
    raise RuntimeError("AdminService constructor anchor not found")
text = text.replace(constructor_old, constructor_new, 1)

dashboard_pattern = re.compile(
    r"    public Map<String, Object> dashboard\(\) \{.*?\n    \}\n\n    public List<Map<String, Object>> majors",
    re.DOTALL,
)
dashboard_replacement = """    public Map<String, Object> dashboard() {
        AdminDashboardStatsRow stats = adminDashboardMapper.findStats();
        return stats.asResponseMap();
    }

    public List<Map<String, Object>> majors"""
text, count = dashboard_pattern.subn(dashboard_replacement, text, count=1)
if count != 1:
    raise RuntimeError(f"expected one dashboard method replacement, got {count}")

start = text.index("public Map<String, Object> dashboard")
end = text.index("public List<Map<String, Object>> majors", start)
body = text[start:end]
if "jdbc." in body or "SELECT " in body or "count(" in body:
    raise RuntimeError("dashboard method still contains JDBC, SQL, or service count calls")

path.write_text(text, encoding="utf-8")
print("AdminService dashboard statistics migrated to MyBatis")
