#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
PLANNER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutPlanner.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/RoomLayoutMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/RoomLayoutMapper.xml"
BASELINE = ROOT / "scripts/ci/backend_modularization_baseline.json"

errors = []
service = SERVICE.read_text(encoding="utf-8")
for token in ("NamedParameterJdbcTemplate", "MapSqlParameterSource", "GeneratedKeyHolder", "jdbc."):
    if token in service:
        errors.append(f"RoomLayoutService must not use direct JDBC token: {token}")
for required in ("RoomLayoutMapper", "RoomLayoutPlanner"):
    if required not in service:
        errors.append(f"RoomLayoutService must depend on {required}")

for path in (PLANNER, MAPPER, XML):
    if not path.exists():
        errors.append(f"missing room layout component: {path.relative_to(ROOT)}")

if PLANNER.exists():
    planner = PLANNER.read_text(encoding="utf-8")
    for forbidden in ("NamedParameterJdbcTemplate", "JdbcTemplate", "@Service", "@Component", "mapper."):
        if forbidden in planner:
            errors.append(f"RoomLayoutPlanner must remain pure Java; found {forbidden}")
    for token in ("buildUnits", "validateUnitSet", "validateItems", "defaultPlacement", "roomTypeForBedCount"):
        if token not in planner:
            errors.append(f"RoomLayoutPlanner missing planning function: {token}")

if XML.exists():
    xml = XML.read_text(encoding="utf-8")
    if "SELECT *" in xml.upper():
        errors.append("RoomLayoutMapper.xml must not use SELECT *")
    for token in (
        'id="lockRoom"',
        'id="lockRoomBeds"',
        "FOR UPDATE",
        "WHERE id = #{roomId}",
        "AND version = #{expectedVersion}",
        "ON DUPLICATE KEY UPDATE",
        "operational_status = 'RETIRED'",
    ):
        if token not in xml:
            errors.append(f"RoomLayoutMapper.xml missing concurrency/layout token: {token}")

baseline = BASELINE.read_text(encoding="utf-8")
if "RoomLayoutService.java" in baseline:
    errors.append("RoomLayoutService must leave the >300 line baseline")

if errors:
    print("room layout componentization contract: FAILED")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("room layout componentization contract: OK")
