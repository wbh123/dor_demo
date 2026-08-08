#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/roomchange/RoomChangeService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/roomchange/mapper/RoomChangeMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/roomchange/RoomChangeMapper.xml"

errors = []
service = SERVICE.read_text(encoding="utf-8")
for forbidden in ["NamedParameterJdbcTemplate", "MapSqlParameterSource", "GeneratedKeyHolder", "jdbc.", "StringBuilder"]:
    if forbidden in service:
        errors.append(f"RoomChangeService 仍直接持有 JDBC/动态 SQL 实现：{forbidden}")

if "residencyService.end(" not in service or "residencyService.assign(" not in service:
    errors.append("换寝执行必须继续复用 ResidencyService.end + assign 维护住宿历史与审计语义")
elif service.index("residencyService.end(") > service.index("residencyService.assign("):
    errors.append("换寝执行必须先结束原住宿，再创建新住宿")

if not MAPPER.exists():
    errors.append("缺少 RoomChangeMapper")
if not XML.exists():
    errors.append("缺少 RoomChangeMapper.xml")

required = [
    "findCandidateRooms", "findStudentRequests", "findAdminRequests",
    "findPolicyMode", "findRequest", "lockRequest",
    "findActiveResidency", "lockActiveResidency", "countActiveRequests",
    "lockActiveRequestIds", "insertRequest", "approveRequest",
    "rejectRequest", "cancelRequest", "cancelActiveRequests",
    "markExecuted", "markFailed", "upsertPolicy", "insertStudentNotification",
]
if MAPPER.exists() and XML.exists():
    mapper = MAPPER.read_text(encoding="utf-8")
    xml = XML.read_text(encoding="utf-8")
    for method in required:
        if method not in mapper or f'id="{method}"' not in xml:
            errors.append(f"RoomChange Mapper 缺少：{method}")
    if "SELECT *" in xml.upper():
        errors.append("RoomChangeMapper.xml 不得使用 SELECT *")
    for lock_id in ["lockRequest", "lockActiveResidency", "lockActiveRequestIds"]:
        marker = f'id="{lock_id}"'
        start = xml.find(marker)
        end = xml.find("</select>", start)
        if start < 0 or end < 0 or "FOR UPDATE" not in xml[start:end].upper():
            errors.append(f"{lock_id} 必须保留 FOR UPDATE 锁语义")

if errors:
    print("room change MyBatis contract failed:")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)
print("room change MyBatis contract: OK")
