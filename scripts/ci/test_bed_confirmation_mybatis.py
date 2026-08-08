#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/bedconfirmation/BedConfirmationService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/bedconfirmation/mapper/BedConfirmationMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/bedconfirmation/BedConfirmationMapper.xml"

errors = []
service = SERVICE.read_text(encoding="utf-8")

if "NamedParameterJdbcTemplate" in service or "jdbc." in service:
    errors.append("BedConfirmationService 仍直接访问 JDBC")
if not MAPPER.exists():
    errors.append("缺少 BedConfirmationMapper.java")
if not XML.exists():
    errors.append("缺少 BedConfirmationMapper.xml")

if MAPPER.exists() and XML.exists():
    mapper = MAPPER.read_text(encoding="utf-8")
    xml = XML.read_text(encoding="utf-8")
    required_methods = [
        "findCurrentResidency", "lockCurrentResidency", "findRoomBeds",
        "findPendingForResidency", "findRooms", "findRoomStudents",
        "lockPendingRequests", "lockActiveAssignments", "lockRoomBeds",
        "findRoomApprovalCandidates", "insertRequest", "assignBed",
        "approveRequest", "rejectRequest", "insertStudentNotification",
    ]
    for method in required_methods:
        if method not in mapper or f'id="{method}"' not in xml:
            errors.append(f"床位核查 Mapper 缺少契约方法：{method}")
    if "COUNT(*) OVER (PARTITION BY request.declared_bed_id)" not in xml:
        errors.append("整寝审核未通过窗口函数一次计算重复床位申报数")
    if "occupied_by_other" not in xml or "occupied_by_self" not in xml:
        errors.append("整寝审核快照未一次返回床位占用冲突事实")
    if "SELECT *" in xml.upper():
        errors.append("BedConfirmationMapper.xml 不得使用 SELECT *")

if "findRoomApprovalCandidates" not in service:
    errors.append("BedConfirmationService 尚未使用一次性整寝审核快照")
if "occupiedBySelf" in service or "SELECT COUNT(*) FROM room_assignment" in service:
    errors.append("整寝审核仍存在逐申请占床查询/N+1 痕迹")

if errors:
    print("bed confirmation MyBatis contract failed:")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("bed confirmation MyBatis contract: OK")
