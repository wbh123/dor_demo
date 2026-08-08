#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/allocation/OptimizedAllocationRunService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/allocation/OptimizedAllocationMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/allocation/OptimizedAllocationMapper.xml"
DIGEST = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/allocation/AllocationInputDigestService.java"
VALIDATOR = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/allocation/OptimizedAllocationConstraintValidator.java"
WRITER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/allocation/AssignmentWriteService.java"
BASELINE = ROOT / "scripts/ci/backend_modularization_baseline.json"

errors = []
for path in (SERVICE, MAPPER, XML, DIGEST, VALIDATOR, WRITER):
    if not path.exists():
        errors.append(f"missing optimized allocation architecture file: {path.relative_to(ROOT)}")

if SERVICE.exists():
    text = SERVICE.read_text(encoding="utf-8")
    for token in ("NamedParameterJdbcTemplate", "JdbcTemplate", "jdbc.", "queryForList", "queryForObject"):
        if token in text:
            errors.append(f"OptimizedAllocationRunService must not directly access JDBC: {token}")
    for required in (
        "AllocationSnapshotReader",
        "AllocationInputDigestService",
        "OptimizedAllocationConstraintValidator",
        "AssignmentWriteService",
        "OptimizedAllocationMapper",
        "CANDIDATE_INSERT_BATCH_SIZE = 200",
    ):
        if required not in text:
            errors.append(f"OptimizedAllocationRunService missing shared architecture token: {required}")
    if "baselineAllocationService.preview" in text:
        errors.append("optimized allocation must not trigger a second baseline database snapshot")

if XML.exists():
    xml = XML.read_text(encoding="utf-8")
    if "SELECT *" in xml.upper():
        errors.append("OptimizedAllocationMapper.xml must not use SELECT *")
    for token in (
        'id="insertCandidates"',
        '<foreach collection="items"',
        'id="lockRun"',
        'id="lockCandidates"',
        "FOR UPDATE",
        'id="markSubmitted"',
    ):
        if token not in xml:
            errors.append(f"OptimizedAllocationMapper.xml missing persistence/concurrency token: {token}")

if DIGEST.exists():
    text = DIGEST.read_text(encoding="utf-8")
    if "AllocationModels.InputSnapshot" not in text or "SHA-256" not in text:
        errors.append("allocation input digest must hash the shared immutable snapshot")
    for token in ("JdbcTemplate", "Mapper", "SELECT "):
        if token in text:
            errors.append(f"AllocationInputDigestService must be database-free: {token}")

if VALIDATOR.exists():
    text = VALIDATOR.read_text(encoding="utf-8")
    if "AllocationModels.InputSnapshot" not in text:
        errors.append("optimized hard constraints must validate against shared snapshot")
    for token in ("JdbcTemplate", "Mapper", "SELECT "):
        if token in text:
            errors.append(f"optimized constraint validator must be database-free: {token}")

if WRITER.exists():
    text = WRITER.read_text(encoding="utf-8")
    for token in ("ADMIN_RANDOM", "ADMIN_OPTIMIZED"):
        if token in text:
            errors.append("AssignmentWriteService must remain assignment-method agnostic")

if SERVICE.exists() and "OptimizedAllocationRunService.java" in BASELINE.read_text(encoding="utf-8"):
    errors.append("OptimizedAllocationRunService must leave the >300 line baseline")

if errors:
    print("optimized allocation architecture contract: FAILED")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)
print("optimized allocation architecture contract: OK")
