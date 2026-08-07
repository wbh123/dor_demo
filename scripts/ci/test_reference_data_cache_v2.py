#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/ReferenceDataCacheService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/AdminCatalogMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/AdminCatalogMapper.xml"
BUILDING_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/BuildingManagementService.java"

for path in (CACHE, MAPPER, XML, BUILDING_SERVICE):
    if not path.exists():
        raise AssertionError(f"缺少静态目录缓存 V2 文件：{path.relative_to(ROOT)}")

cache = CACHE.read_text(encoding="utf-8")
mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")
building_service = BUILDING_SERVICE.read_text(encoding="utf-8")

for key in ("dorm:building:", ":static", ":floors"):
    if key not in cache:
        raise AssertionError(f"静态缓存缺少规范 key 片段：{key}")
for method in ("buildingStatic", "buildingFloors", "invalidateBuilding"):
    if method not in cache:
        raise AssertionError(f"ReferenceDataCacheService 缺少方法：{method}")
for mapper_method in ("findAllBuildingStatic", "findBuildingStatic", "findAllFloors", "findFloors"):
    if mapper_method not in mapper or mapper_method not in xml:
        raise AssertionError(f"楼栋/楼层缓存必须通过 MyBatis 批量/读穿方法：{mapper_method}")

if "findAllBuildingStatic" not in cache or "findAllFloors" not in cache:
    raise AssertionError("启动预热必须批量读取全部楼栋静态信息和楼层，禁止逐楼栋预热")
if "for (" in cache.split("public void warmUp()", 1)[1].split("public List<Map<String, Object>> majors", 1)[0] and "findBuildingStatic(" in cache:
    raise AssertionError("启动预热不得在循环中逐楼栋回源数据库")

for dynamic_token in ("resident_count", "active_residents", "available_beds", "hold"):
    if dynamic_token in xml.lower().split("findAllBuildingStatic", 1)[-1].split("</mapper>", 1)[0]:
        raise AssertionError(f"楼栋静态缓存 SQL 不得包含动态事实：{dynamic_token}")

if "referenceDataCacheService.invalidateBuilding(buildingId)" not in building_service:
    raise AssertionError("楼栋更新成功后必须事务提交后失效楼栋/楼层缓存")
if "TransactionSynchronizationManager" not in cache or "afterCommit" not in cache:
    raise AssertionError("静态缓存失效必须保留事务提交后执行语义")

print("Reference data Redis cache V2 contract: OK")
