#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyPolicyService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/residency/mapper/RoomOccupancySnapshotMapper.java"
ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/residency/model/persistence/RoomOccupancySnapshotRow.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/residency/RoomOccupancySnapshotMapper.xml"

for path in (SERVICE, MAPPER, ROW, XML):
    if not path.exists():
        raise AssertionError(f"缺少住宿快照读模型文件：{path.relative_to(ROOT)}")

service = SERVICE.read_text(encoding="utf-8")
mapper = MAPPER.read_text(encoding="utf-8")
row = ROW.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")

if "RoomOccupancySnapshotMapper" not in service:
    raise AssertionError("ResidencyPolicyService 必须依赖 RoomOccupancySnapshotMapper")
if "findSnapshot(" not in mapper:
    raise AssertionError("住宿快照 Mapper 必须提供单房间读取入口")
if "available_beds" not in xml or "availableBeds" not in row:
    raise AssertionError("住宿快照必须包含当前可用启用床位数量")

for method_name in ("activeResidentCount", "unknownBedResidentCount", "availableCapacity", "availableBedCount"):
    marker = f"public int {method_name}(long roomId)"
    if marker not in service:
        raise AssertionError(f"缺少兼容方法：{method_name}")
    start = service.index(marker)
    end = service.find("\n    public ", start + len(marker))
    if end < 0:
        end = service.find("\n    private ", start + len(marker))
    body = service[start:end]
    if "jdbc." in body or "SELECT " in body or "NOT EXISTS" in body:
        raise AssertionError(f"{method_name} 必须委托快照读模型，不得直接执行 SQL")

room_start = service.index("public Map<String, Object> room(long roomId, boolean forUpdate)")
room_end = service.find("\n    public ", room_start + 20)
room_body = service[room_start:room_end]
if "forUpdate" not in room_body or "FOR UPDATE" not in room_body:
    raise AssertionError("写路径 room(..., true) 必须继续保留 FOR UPDATE 语义")
if "findSnapshot" not in room_body:
    raise AssertionError("非锁定 room(..., false) 必须走住宿快照 Mapper")

if "assignment_status = 'ACTIVE'" not in xml:
    raise AssertionError("住宿快照必须只统计 ACTIVE 入住记录")
if "operational_status = 'ENABLED'" not in xml:
    raise AssertionError("可用床位统计必须只统计 ENABLED 床位")
if "LEFT JOIN room_assignment" not in xml:
    raise AssertionError("可用床位统计应通过集合化反连接/聚合避免逐床相关子查询")
if "SELECT *" in xml:
    raise AssertionError("住宿快照 XML 禁止 SELECT *")

print("Residency snapshot read-model contract: OK")
