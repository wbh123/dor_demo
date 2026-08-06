#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/RoomCatalogMapper.java"
ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/model/persistence/RoomCatalogRow.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/RoomCatalogMapper.xml"
TEST = ROOT / "backend-java/server/src/test/java/com/wust/dormitory/admin/RoomManagementQueryServiceTest.java"

for path in (SERVICE, MAPPER, ROW, XML, TEST):
    if not path.exists():
        raise AssertionError(f"缺少房间目录 MyBatis 文件：{path.relative_to(ROOT)}")

service = SERVICE.read_text(encoding="utf-8")
mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")
row = ROW.read_text(encoding="utf-8")

if "RoomCatalogMapper" not in service or "roomCatalogMapper.findRooms" not in service:
    raise AssertionError("RoomManagementService 必须通过 RoomCatalogMapper 查询房间目录")
rooms_start = service.index("public List<Map<String, Object>> rooms")
rooms_end = service.index("@Transactional", rooms_start)
rooms_method = service[rooms_start:rooms_end]
if "SELECT " in rooms_method or "jdbc." in rooms_method:
    raise AssertionError("房间目录查询不得在 Service 中保留 SQL 或 JDBC 调用")

if "@Mapper" not in mapper or "@Select" in mapper:
    raise AssertionError("RoomCatalogMapper 必须使用 XML SQL，禁止 SQL 注解")
if 'namespace="com.wust.dormitory.admin.mapper.RoomCatalogMapper"' not in xml:
    raise AssertionError("RoomCatalogMapper XML namespace 不匹配")
if xml.count("FROM room_assignment") != 1:
    raise AssertionError("房间目录必须只聚合扫描一次 room_assignment")
if "GROUP BY ra.room_id" not in xml or "occupancy.active_resident_count" not in xml:
    raise AssertionError("房间目录必须通过一次在住聚合关联房间")
if "operational_status &lt;&gt; 'RETIRED'" not in xml:
    raise AssertionError("房间床位总数必须排除退役床位")
for token in (
        "r.education_level_scope",
        "building_gender_restriction",
        "building_education_level_scope",
        "building_resident_scope",
):
    if token not in xml:
        raise AssertionError(f"房间目录缺少范围字段：{token}")
if "SELECT *" in xml:
    raise AssertionError("Mapper XML 不得使用 SELECT *")
for key in (
        "building_name",
        "education_level_scope",
        "building_education_level_scope",
        "remaining_capacity",
):
    if f'response.put("{key}"' not in row:
        raise AssertionError(f"类型化结果必须保持响应字段：{key}")

print("Admin room catalog MyBatis contract passed")
