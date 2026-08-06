#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/StudentAdminMapper.java"
QUERY = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/model/query/StudentCatalogQuery.java"
ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/model/persistence/StudentCatalogRow.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/StudentAdminMapper.xml"
TEST = ROOT / "backend-java/server/src/test/java/com/wust/dormitory/admin/StudentAdminQueryServiceTest.java"

for path in (SERVICE, MAPPER, QUERY, ROW, XML, TEST):
    if not path.exists():
        raise AssertionError(f"缺少学生查询 MyBatis 文件：{path.relative_to(ROOT)}")

service = SERVICE.read_text(encoding="utf-8")
mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")
row = ROW.read_text(encoding="utf-8")
query = QUERY.read_text(encoding="utf-8")

if "studentAdminMapper.countStudents" not in service or "studentAdminMapper.findStudents" not in service:
    raise AssertionError("AdminService.students 必须委托 StudentAdminMapper")
start = service.index("public Map<String, Object> students")
end = service.index("@Transactional", start)
body = service[start:end]
if "jdbc." in body or "SELECT " in body or "StringBuilder where" in body:
    raise AssertionError("学生分页查询不得在 Service 中保留 JDBC、SQL 或动态条件拼接")
if "public List<Map<String, Object>> rooms(" in service:
    raise AssertionError("AdminService 中无调用方的旧 rooms 查询必须删除")

if "@Mapper" not in mapper or "@Select" in mapper:
    raise AssertionError("StudentAdminMapper 必须使用 XML SQL，禁止 SQL 注解")
if 'namespace="com.wust.dormitory.admin.mapper.StudentAdminMapper"' not in xml:
    raise AssertionError("StudentAdminMapper XML namespace 不匹配")
if xml.count('<include refid="StudentCatalogFilters"/>') != 2:
    raise AssertionError("计数与列表查询必须复用同一筛选条件")
for token in (
        "s.student_number LIKE #{keywordPattern}",
        "s.student_name LIKE #{keywordPattern}",
        "s.gender = #{gender}",
        "s.major_id = #{majorId}",
        "ORDER BY s.student_number",
        "LIMIT #{limit} OFFSET #{offset}",
):
    if token not in xml:
        raise AssertionError(f"学生查询缺少稳定条件或分页：{token}")
if "SELECT *" in xml:
    raise AssertionError("StudentAdminMapper XML 不得使用 SELECT *")
for key in ("student_number", "major_name", "account_status"):
    if f'response.put("{key}"' not in row:
        raise AssertionError(f"学生类型化结果缺少响应字段：{key}")
for field in ("keywordPattern", "gender", "majorId", "limit", "offset"):
    if field not in query:
        raise AssertionError(f"学生查询对象缺少字段：{field}")

print("Admin student query MyBatis contract passed")
