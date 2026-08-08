#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/allocation/AdminAllocationService.java"
READER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/allocation/AllocationSnapshotReader.java"
PLANNER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/allocation/BaselineAllocationPlanner.java"
COMMIT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/allocation/AllocationCommitService.java"
SNAPSHOT_XML = ROOT / "backend-java/server/src/main/resources/mapper/allocation/AllocationSnapshotMapper.xml"
COMMIT_XML = ROOT / "backend-java/server/src/main/resources/mapper/allocation/AllocationCommitMapper.xml"
BASELINE = ROOT / "scripts/ci/backend_modularization_baseline.json"

errors = []
for path in (SERVICE, READER, PLANNER, COMMIT, SNAPSHOT_XML, COMMIT_XML):
    if not path.exists():
        errors.append(f"missing allocation architecture file: {path.relative_to(ROOT)}")

if SERVICE.exists():
    text = SERVICE.read_text(encoding="utf-8")
    for token in ("NamedParameterJdbcTemplate", "JdbcTemplate", "jdbc.", "SELECT ", "INSERT INTO"):
        if token in text:
            errors.append(f"AdminAllocationService must remain orchestration-only; found {token}")
    for required in ("AllocationSnapshotReader", "AllocationCommitService", "BaselineAllocationPlanner"):
        if required not in text:
            errors.append(f"AdminAllocationService missing {required}")

if PLANNER.exists():
    text = PLANNER.read_text(encoding="utf-8")
    for token in ("NamedParameterJdbcTemplate", "JdbcTemplate", "Mapper", "@Service", "@Component"):
        if token in text:
            errors.append(f"BaselineAllocationPlanner must be pure Java; found {token}")
    for token in ("randomSeed", "lockedTeams", "TEAM_ROOM_CAPACITY_INSUFFICIENT", "NO_AVAILABLE_BED"):
        if token not in text:
            errors.append(f"BaselineAllocationPlanner missing deterministic/business token: {token}")

if SNAPSHOT_XML.exists():
    xml = SNAPSHOT_XML.read_text(encoding="utf-8")
    if "SELECT *" in xml.upper():
        errors.append("AllocationSnapshotMapper.xml must not use SELECT *")
    for statement in ("findBatchId", "findEligibleStudents", "findAvailableBeds", "findLockedTeamMembers"):
        if f'id="{statement}"' not in xml:
            errors.append(f"AllocationSnapshotMapper.xml missing fixed snapshot query {statement}")
    if "teamId" in xml or "team_id = #{team" in xml:
        errors.append("Allocation snapshot must not issue per-team member queries")
    if "scoped_rooms" not in xml or "bed_scope_config" not in xml:
        errors.append("available bed query must use set-based scope CTEs")

if COMMIT.exists():
    text = COMMIT.read_text(encoding="utf-8")
    for token in ("NamedParameterJdbcTemplate", "JdbcTemplate", "jdbc."):
        if token in text:
            errors.append(f"AllocationCommitService must use mapper persistence; found {token}")
    if "@Transactional" not in text:
        errors.append("AllocationCommitService.commit must remain transactional")

if COMMIT_XML.exists():
    xml = COMMIT_XML.read_text(encoding="utf-8")
    for token in ("FOR UPDATE", "idempotency_key", "insertUnassignedResults", "completeTeams", "finishBatch"):
        if token not in xml:
            errors.append(f"AllocationCommitMapper.xml missing commit invariant token: {token}")

if SERVICE.exists() and "AdminAllocationService.java" in BASELINE.read_text(encoding="utf-8"):
    errors.append("AdminAllocationService must leave the >300 line baseline")

if errors:
    print("admin allocation architecture contract: FAILED")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)
print("admin allocation architecture contract: OK")
