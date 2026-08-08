#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/AdminService.java"
MAJOR_MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/MajorManagementMapper.java"
MAJOR_XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/MajorManagementMapper.xml"
PREP_MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/mapper/BatchPreparationMapper.java"
PREP_XML = ROOT / "backend-java/server/src/main/resources/mapper/admin/BatchPreparationMapper.xml"
BASELINE = ROOT / "scripts/ci/backend_modularization_baseline.json"

errors = []
service = SERVICE.read_text(encoding="utf-8")

for forbidden in (
    "NamedParameterJdbcTemplate",
    "MapSqlParameterSource",
    "GeneratedKeyHolder",
    "jdbc.",
    "ObjectMapper",
    "saveStudent(",
    "importStudents(",
    "updateRoom(",
    "createBatch(",
    "changeBatchStatus(",
    "allocationPreview(",
    "allocationCommit(",
    "buildAllocation(",
):
    if forbidden in service:
        errors.append(f"AdminService must not retain legacy responsibility/token: {forbidden}")

for required in (
    "AdminDashboardMapper",
    "BatchCatalogMapper",
    "ReferenceDataCacheService",
    "MajorManagementMapper",
    "BatchPreparationMapper",
):
    if required not in service:
        errors.append(f"AdminService must depend on {required}")

for path in (MAJOR_MAPPER, MAJOR_XML, PREP_MAPPER, PREP_XML):
    if not path.exists():
        errors.append(f"missing admin convergence persistence file: {path.relative_to(ROOT)}")

if MAJOR_XML.exists():
    text = MAJOR_XML.read_text(encoding="utf-8")
    for token in ("INSERT INTO major", "UPDATE major", "WHERE id = #{id}"):
        if token not in text:
            errors.append(f"MajorManagementMapper.xml missing contract token: {token}")
    if "SELECT *" in text.upper():
        errors.append("MajorManagementMapper.xml must not use SELECT *")

if PREP_XML.exists():
    text = PREP_XML.read_text(encoding="utf-8")
    for token in (
        "batch_student_eligibility",
        "batch_building_scope",
        "batch_status = 'DRAFT'",
    ):
        if token not in text:
            errors.append(f"BatchPreparationMapper.xml missing contract token: {token}")
    if "SELECT *" in text.upper():
        errors.append("BatchPreparationMapper.xml must not use SELECT *")

baseline = BASELINE.read_text(encoding="utf-8")
if "AdminService.java" in baseline:
    errors.append("AdminService must leave the >300 line baseline after convergence")

if errors:
    print("admin service scope convergence contract: FAILED")
    for error in errors:
        print(f"- {error}")
    raise SystemExit(1)

print("admin service scope convergence contract: OK")
