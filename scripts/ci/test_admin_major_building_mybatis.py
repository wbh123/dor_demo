#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java"
CACHE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/ReferenceDataCacheService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/AdminCatalogMapper.java"
MAJOR_ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/model/persistence/MajorCatalogRow.java"
BUILDING_ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/model/persistence/BuildingCatalogRow.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/AdminCatalogMapper.xml"
TEST = ROOT / "backend-java/server/src/test/java/com/wust/dormitory/admin/AdminCatalogQueryServiceTest.java"

for path in (SERVICE, CACHE, MAPPER, MAJOR_ROW, BUILDING_ROW, XML, TEST):
    if not path.exists():
        raise AssertionError(f"缺少专业或楼栋目录 MyBatis 文件：{path.relative_to(ROOT)}")

service = SERVICE.read_text(encoding="utf-8")
cache = CACHE.read_text(encoding="utf-8")
mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")
major_row = MAJOR_ROW.read_text(encoding="utf-8")
building_row = BUILDING_ROW.read_text(encoding="utf-8")

if "referenceDataCacheService.majors(enabled)" not in service:
    raise AssertionError("AdminService.majors 必须委托专业目录读穿缓存")
if "adminCatalogMapper.findMajors(enabled)" not in cache:
    raise AssertionError("专业目录缓存未命中时必须回源 AdminCatalogMapper")
if "adminCatalogMapper.findBuildings" not in service:
    raise AssertionError("AdminService.buildings 必须委托 AdminCatalogMapper")

for method_name in ("majors", "buildings"):
    start = service.index(f"public List<Map<String, Object>> {method_name}")
    next_method = service.find("\n    public ", start + 10)
    next_transaction = service.find("\n    @Transactional", start + 10)
    end = min(value for value in (next_method, next_transaction) if value >= 0)
    body = service[start:end]
    if "jdbc." in body or "SELECT " in body:
        raise AssertionError(f"AdminService.{method_name} 不得保留 JDBC 或 SQL")

if "@Mapper" not in mapper or "@Select" in mapper:
    raise AssertionError("AdminCatalogMapper 必须使用 XML SQL，禁止 SQL 注解")
if 'namespace="com.wust.dormitory.admin.mapper.AdminCatalogMapper"' not in xml:
    raise AssertionError("AdminCatalogMapper XML namespace 不匹配")
if "ORDER BY major_code" not in xml or "ORDER BY b.building_code" not in xml:
    raise AssertionError("专业和楼栋目录必须使用确定排序")
if "bed.operational_status &lt;&gt; 'RETIRED'" not in xml:
    raise AssertionError("楼栋床位统计必须排除退役床位")
for token in (
        "b.education_level_scope",
        "b.resident_scope",
        "COUNT(DISTINCT r.id)",
):
    if token not in xml:
        raise AssertionError(f"楼栋目录缺少正式字段或聚合：{token}")
if "SELECT *" in xml:
    raise AssertionError("AdminCatalogMapper XML 不得使用 SELECT *")
for key in ("major_code", "created_at"):
    if f'response.put("{key}"' not in major_row:
        raise AssertionError(f"专业类型化结果缺少响应字段：{key}")
for key in ("campus_name", "education_level_scope", "bed_count"):
    if f'response.put("{key}"' not in building_row:
        raise AssertionError(f"楼栋类型化结果缺少响应字段：{key}")

print("Admin major and building catalog MyBatis contract passed")
