#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/AdminDashboardMapper.java"
ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/model/persistence/AdminDashboardStatsRow.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/AdminDashboardMapper.xml"
TEST = ROOT / "backend-java/server/src/test/java/com/wust/dormitory/admin/AdminDashboardQueryServiceTest.java"

for path in (SERVICE, MAPPER, ROW, XML, TEST):
    if not path.exists():
        raise AssertionError(f"缺少管理工作台 MyBatis 文件：{path.relative_to(ROOT)}")

service = SERVICE.read_text(encoding="utf-8")
mapper = MAPPER.read_text(encoding="utf-8")
row = ROW.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")

if "adminDashboardMapper.findStats()" not in service:
    raise AssertionError("AdminService.dashboard 必须委托 AdminDashboardMapper")
start = service.index("public Map<String, Object> dashboard")
end = service.index("public List<Map<String, Object>> majors", start)
body = service[start:end]
if "jdbc." in body or "SELECT " in body or "count(" in body:
    raise AssertionError("管理工作台统计不得在 Service 中保留 JDBC、SQL 或逐项计数")

if "@Mapper" not in mapper or "@Select" in mapper:
    raise AssertionError("AdminDashboardMapper 必须使用 XML SQL，禁止 SQL 注解")
if 'namespace="com.wust.dormitory.admin.mapper.AdminDashboardMapper"' not in xml:
    raise AssertionError("AdminDashboardMapper XML namespace 不匹配")
if xml.count("<select ") != 1:
    raise AssertionError("管理工作台统计必须由一个 Mapper 查询返回")
for token in (
        "FROM major WHERE enabled = 1",
        "FROM student) AS student_count",
        "FROM student WHERE gender = 'M'",
        "FROM student WHERE gender = 'F'",
        "FROM room) AS room_count",
        "FROM bed WHERE operational_status = 'ENABLED'",
        "COUNT(DISTINCT student_id)",
        "FROM room_assignment",
        "assignment_status = 'ACTIVE'",
        "batch_status IN ('PUBLISHED', 'OPEN', 'PAUSED')",
):
    if token not in xml:
        raise AssertionError(f"工作台统计缺少当前业务口径：{token}")
if "FROM bed_assignment) AS active_assignment_count" in xml:
    raise AssertionError("已分配人数不得继续按历史/当前床位分配行数统计")
if "SELECT *" in xml:
    raise AssertionError("AdminDashboardMapper XML 不得使用 SELECT *")

for key in (
        "majorCount",
        "studentCount",
        "maleStudentCount",
        "femaleStudentCount",
        "roomCount",
        "bedCount",
        "activeAssignmentCount",
        "openBatchCount",
):
    if f'response.put("{key}"' not in row:
        raise AssertionError(f"工作台类型化结果缺少响应字段：{key}")

print("Admin dashboard MyBatis contract passed")