#!/usr/bin/env python3
# Temporary branch-only migration helper; removed after successful execution.
from pathlib import Path
import re

service_path = Path("backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java")
text = service_path.read_text(encoding="utf-8")

text = text.replace(
    "import com.wust.dormitory.admin.mapper.AdminDashboardMapper;\n",
    "import com.wust.dormitory.admin.mapper.AdminDashboardMapper;\n"
    "import com.wust.dormitory.admin.mapper.BatchCatalogMapper;\n",
    1,
)
text = text.replace(
    "import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;\n",
    "import com.wust.dormitory.admin.model.persistence.BatchCatalogRow;\n"
    "import com.wust.dormitory.admin.model.persistence.BuildingCatalogRow;\n",
    1,
)
text = text.replace(
    "    private final AdminDashboardMapper adminDashboardMapper;\n",
    "    private final AdminDashboardMapper adminDashboardMapper;\n"
    "    private final BatchCatalogMapper batchCatalogMapper;\n",
    1,
)
constructor_old = """            AdminCatalogMapper adminCatalogMapper,
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
constructor_new = """            AdminCatalogMapper adminCatalogMapper,
            StudentAdminMapper studentAdminMapper,
            AdminDashboardMapper adminDashboardMapper,
            BatchCatalogMapper batchCatalogMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.adminCatalogMapper = adminCatalogMapper;
        this.studentAdminMapper = studentAdminMapper;
        this.adminDashboardMapper = adminDashboardMapper;
        this.batchCatalogMapper = batchCatalogMapper;
    }
"""
if constructor_old not in text:
    raise RuntimeError("AdminService constructor anchor not found")
text = text.replace(constructor_old, constructor_new, 1)

pattern = re.compile(
    r"    public List<Map<String, Object>> batches\(\) \{.*?\n    \}\n\n    @Transactional\n    public long createBatch",
    re.DOTALL,
)
replacement = """    public List<Map<String, Object>> batches() {
        return batchCatalogMapper.findBatches().stream()
                .map(BatchCatalogRow::asResponseMap)
                .toList();
    }

    @Transactional
    public long createBatch"""
text, count = pattern.subn(replacement, text, count=1)
if count != 1:
    raise RuntimeError(f"expected one batches replacement, got {count}")
service_path.write_text(text, encoding="utf-8")

for relative in (
    "backend-java/server/src/test/java/com/wust/dormitory/admin/AdminCatalogQueryServiceTest.java",
    "backend-java/server/src/test/java/com/wust/dormitory/admin/StudentAdminQueryServiceTest.java",
    "backend-java/server/src/test/java/com/wust/dormitory/admin/AdminDashboardQueryServiceTest.java",
):
    path = Path(relative)
    source = path.read_text(encoding="utf-8")
    if "import com.wust.dormitory.admin.mapper.BatchCatalogMapper;" not in source:
        source = source.replace(
            "import com.wust.dormitory.admin.mapper.AdminDashboardMapper;\n",
            "import com.wust.dormitory.admin.mapper.AdminDashboardMapper;\n"
            "import com.wust.dormitory.admin.mapper.BatchCatalogMapper;\n",
            1,
        )
    declaration_anchor = "        AdminDashboardMapper dashboardMapper = mock(AdminDashboardMapper.class);\n"
    if declaration_anchor not in source:
        raise RuntimeError(f"dashboard mapper declaration anchor not found: {relative}")
    source = source.replace(
        declaration_anchor,
        declaration_anchor + "        BatchCatalogMapper batchCatalogMapper = mock(BatchCatalogMapper.class);\n",
    )
    source = source.replace(
        "                dashboardMapper);",
        "                dashboardMapper,\n                batchCatalogMapper);",
    )
    path.write_text(source, encoding="utf-8")

print("Admin batch catalog service and constructor tests migrated")
