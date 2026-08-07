#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/residency/BatchRoomLockService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/residency/mapper/RoomOccupancySnapshotMapper.java"
ROW = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/residency/model/persistence/RoomOccupancySnapshotRow.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/residency/RoomOccupancySnapshotMapper.xml"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> None:
    for path in (SERVICE, MAPPER, ROW, XML):
        require(path.exists(), f"缺少住宿批量快照文件：{path.relative_to(ROOT)}")

    service = SERVICE.read_text(encoding="utf-8")
    mapper = MAPPER.read_text(encoding="utf-8")
    xml = XML.read_text(encoding="utf-8")
    normalized = " ".join(xml.upper().split())

    start = service.index("public Map<String, Object> preview")
    end = service.index("public void requirePublishable", start)
    preview = service[start:end]
    require("roomOccupancySnapshotMapper.findSnapshots" in preview,
            "批次预检必须一次读取住宿快照")
    for token in (
        "policy.room(",
        "policy.activeResidentCount(",
        "policy.unknownBedResidentCount(",
        "jdbc.queryForList",
    ):
        require(token not in preview, f"批次预检循环不得保留逐房间查询：{token}")

    require("findSnapshots" in mapper, "住宿快照 Mapper 缺少 findSnapshots")
    require('id="findSnapshots"' in xml, "住宿快照 XML 缺少 findSnapshots")
    require("<FOREACH COLLECTION=\"ROOMIDS\"" in normalized,
            "住宿快照必须使用 roomIds 批量查询")
    require("RESIDENT_STATS AS" in normalized,
            "住宿快照必须预聚合有效入住统计")
    require("GROUP BY RA.ROOM_ID" in normalized,
            "住宿快照必须按 room_id 聚合")
    require("LEFT JOIN ACTIVE_BATCH_ROOM_LOCK CONFLICT_LOCK" in normalized,
            "住宿快照必须一次带回活动批次冲突")
    require("SELECT *" not in normalized, "住宿快照高频查询禁止 SELECT *")

    print("Room occupancy snapshot MyBatis performance contract: OK")


if __name__ == "__main__":
    main()
