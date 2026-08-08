#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/roomexchange/mapper/RoomExchangeMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/roomexchange/RoomExchangeMapper.xml"

errors = []
service = SERVICE.read_text(encoding="utf-8")
for forbidden in ["NamedParameterJdbcTemplate", "MapSqlParameterSource", "GeneratedKeyHolder", "jdbc."]:
    if forbidden in service:
        errors.append(f"RoomExchangeService 仍直接持有 JDBC 实现：{forbidden}")
if "residencyPolicy.student(number(candidate.get" in service or ".filter(candidate -> compatible(" in service:
    errors.append("寝室交换候选仍存在逐候选学生/房间查询 N+1")
if 'list("ALL", "", null).stream()' in service:
    errors.append("按 exchangeId 读取仍先加载全部交换记录再在 Java 中过滤")
if "residencyService.end(" not in service or service.count("residencyService.assign(") < 2:
    errors.append("交换执行必须继续复用 ResidencyService 结束双方住宿并创建两条新住宿")

if not MAPPER.exists():
    errors.append("缺少 RoomExchangeMapper")
if not XML.exists():
    errors.append("缺少 RoomExchangeMapper.xml")

required = [
    "findCompatibleCandidates", "findStudentRequests", "findAdminRequests",
    "findRequestView", "findPolicyMode", "lockRequest", "lockActiveResidencies",
    "findActiveResidency", "lockActiveResidency", "insertRequest",
    "insertParticipantLock", "deleteParticipantLocks", "rejectByTarget",
    "acceptByTarget", "approveRequest", "rejectByAdmin", "cancelRequest",
    "markExecuted", "upsertPolicy",
]
if MAPPER.exists() and XML.exists():
    mapper = MAPPER.read_text(encoding="utf-8")
    xml = XML.read_text(encoding="utf-8")
    for method in required:
        if method not in mapper or f'id="{method}"' not in xml:
            errors.append(f"RoomExchange Mapper 缺少：{method}")
    upper = xml.upper()
    if "SELECT *" in upper or "EXCHANGE_ROW.*" in upper:
        errors.append("RoomExchangeMapper.xml 必须使用显式列，不得 SELECT *")
    for lock_id in ["lockRequest", "lockActiveResidencies", "lockActiveResidency"]:
        marker = f'id="{lock_id}"'
        start = xml.find(marker)
        end = xml.find("</select>", start)
        if start < 0 or end < 0 or "FOR UPDATE" not in xml[start:end].upper():
            errors.append(f"{lock_id} 必须保留 FOR UPDATE 锁语义")
    marker = 'id="findCompatibleCandidates"'
    start = xml.find(marker)
    end = xml.find("</select>", start)
    candidate_sql = xml[start:end] if start >= 0 and end >= 0 else ""
    for token in ["gender_restriction", "resident_scope", "LIMIT 20"]:
        if token not in candidate_sql:
            errors.append(f"候选集合查询缺少兼容性/限流条件：{token}")

if errors:
    print("room exchange MyBatis contract failed:")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)
print("room exchange MyBatis and batching contract: OK")
