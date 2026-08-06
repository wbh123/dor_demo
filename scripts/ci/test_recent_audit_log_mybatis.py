#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminController.java"
ADMIN_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java"
QUERY_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/audit/RecentAuditLogQueryService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/audit/mapper/RecentAuditLogMapper.java"
ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/audit/model/persistence/RecentAuditLogRow.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/audit/RecentAuditLogMapper.xml"
TEST = ROOT / "backend-java/server/src/test/java/com/wust/dormitory/audit/RecentAuditLogQueryServiceTest.java"

for path in (CONTROLLER, ADMIN_SERVICE, QUERY_SERVICE, MAPPER, ROW, XML, TEST):
    if not path.exists():
        raise AssertionError(f"缺少最近审计记录 MyBatis 文件：{path.relative_to(ROOT)}")

controller = CONTROLLER.read_text(encoding="utf-8")
admin_service = ADMIN_SERVICE.read_text(encoding="utf-8")
query_service = QUERY_SERVICE.read_text(encoding="utf-8")
mapper = MAPPER.read_text(encoding="utf-8")
row = ROW.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")

if "recentAuditLogQueryService.list" not in controller:
    raise AssertionError("基础审计列表必须委托 RecentAuditLogQueryService")
if "adminService.auditLogs" in controller:
    raise AssertionError("控制器不得继续调用 AdminService.auditLogs")
if "public List<Map<String, Object>> auditLogs" in admin_service:
    raise AssertionError("AdminService 中旧审计 JDBC 查询必须删除")
if "NamedParameterJdbcTemplate" in query_service or "SELECT " in query_service:
    raise AssertionError("RecentAuditLogQueryService 不得直接访问 JDBC 或包含 SQL")
if "Math.min(Math.max(limit, 1), 500)" not in query_service:
    raise AssertionError("基础审计列表必须保持 1—500 条限制")

if "@Mapper" not in mapper or "@Select" in mapper:
    raise AssertionError("RecentAuditLogMapper 必须使用 XML SQL，禁止 SQL 注解")
if 'namespace="com.wust.dormitory.audit.mapper.RecentAuditLogMapper"' not in xml:
    raise AssertionError("RecentAuditLogMapper XML namespace 不匹配")
if "ORDER BY occurred_at DESC" not in xml or "LIMIT #{limit}" not in xml:
    raise AssertionError("基础审计列表必须保持按发生时间倒序和限制条数")
if "SELECT *" in xml:
    raise AssertionError("RecentAuditLogMapper XML 不得使用 SELECT *")
for column in (
        "id",
        "request_id",
        "operator_user_id",
        "operator_type",
        "action_type",
        "resource_type",
        "resource_id",
        "result_status",
        "reason",
        "occurred_at",
):
    if column not in xml:
        raise AssertionError(f"基础审计列表缺少既有字段：{column}")
    if f'response.put("{column}"' not in row:
        raise AssertionError(f"类型化审计结果缺少响应字段：{column}")

print("Recent audit log MyBatis contract passed")
