#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/RoomManagementMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/RoomManagementMapper.xml"

errors = []
service = SERVICE.read_text(encoding="utf-8")
if "NamedParameterJdbcTemplate" in service or "jdbc." in service:
    errors.append("RoomManagementService 仍直接访问 JDBC")
if "for (int floor" in service or "for (int position" in service:
    errors.append("楼层/床位创建仍使用逐行数据库写入循环")
if not MAPPER.exists():
    errors.append("缺少 RoomManagementMapper.java")
if not XML.exists():
    errors.append("缺少 RoomManagementMapper.xml")

if MAPPER.exists() and XML.exists():
    mapper = MAPPER.read_text(encoding="utf-8")
    xml = XML.read_text(encoding="utf-8")
    required = [
        "findBuildings", "findDefaultCampusId", "countBuildingByCode",
        "insertBuilding", "batchInsertFloors", "findBuildingForValidation",
        "findFloorId", "countRoomNumber", "insertRoom", "batchInsertBeds",
        "lockRoomForUpdate", "countPhysicalBeds", "countIncompatibleResidents",
        "updateRoom",
    ]
    for method in required:
        if method not in mapper or f'id="{method}"' not in xml:
            errors.append(f"房间管理 Mapper 缺少契约方法：{method}")
    if xml.count("<foreach") < 2:
        errors.append("楼层和床位必须使用 MyBatis foreach 批量写入")
    if 'id="lockRoomForUpdate"' in xml and "FOR UPDATE" not in xml:
        errors.append("房间更新必须保留 FOR UPDATE 锁定")
    if "SELECT *" in xml.upper():
        errors.append("RoomManagementMapper.xml 不得使用 SELECT *")

if errors:
    print("room management MyBatis contract failed:")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)
print("room management MyBatis contract: OK")
