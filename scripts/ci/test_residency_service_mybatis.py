#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/residency/mapper/ResidencyMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/residency/ResidencyMapper.xml"

errors = []
service = SERVICE.read_text(encoding="utf-8")
for forbidden in ["NamedParameterJdbcTemplate", "MapSqlParameterSource", "GeneratedKeyHolder", "jdbc."]:
    if forbidden in service:
        errors.append(f"ResidencyService 仍直接持有 JDBC 实现：{forbidden}")

if not MAPPER.exists():
    errors.append("缺少 ResidencyMapper")
if not XML.exists():
    errors.append("缺少 ResidencyMapper.xml")

required = [
    "findCurrentResidency", "findResidencies", "findRoomSummaries",
    "findBedRoom", "lockActiveResidency", "insertAssignment",
    "lockResidency", "lockBed", "countOtherActiveBedOccupants",
    "confirmBed", "endResidency", "findResidency", "insertHistory",
]
if MAPPER.exists() and XML.exists():
    mapper = MAPPER.read_text(encoding="utf-8")
    xml = XML.read_text(encoding="utf-8")
    for method in required:
        if method not in mapper or f'id="{method}"' not in xml:
            errors.append(f"Residency Mapper 缺少：{method}")
    if "SELECT *" in xml.upper():
        errors.append("ResidencyMapper.xml 不得使用 SELECT *")
    for lock_id in ["lockActiveResidency", "lockResidency", "lockBed"]:
        marker = f'id="{lock_id}"'
        start = xml.find(marker)
        end = xml.find("</select>", start)
        if start < 0 or end < 0 or "FOR UPDATE" not in xml[start:end].upper():
            errors.append(f"{lock_id} 必须保留 FOR UPDATE 锁语义")

if errors:
    print("residency service MyBatis contract failed:")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)
print("residency service MyBatis contract: OK")
