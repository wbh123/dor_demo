#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/analytics/BatchAnalyticsSnapshotService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/analytics/mapper/BatchAnalyticsSnapshotMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/analytics/BatchAnalyticsSnapshotMapper.xml"
BASELINE = ROOT / "scripts/ci/backend_modularization_baseline.json"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


service = SERVICE.read_text(encoding="utf-8")
require("NamedParameterJdbcTemplate" not in service, "BatchAnalyticsSnapshotService must not depend on JDBC")
require("jdbc." not in service, "BatchAnalyticsSnapshotService must not execute JDBC")
require("BatchAnalyticsSnapshotMapper" in service, "analytics snapshot persistence must use a dedicated mapper")
require(MAPPER.exists(), "BatchAnalyticsSnapshotMapper.java is required")
require(XML.exists(), "BatchAnalyticsSnapshotMapper.xml is required")

mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")
for method in (
    "findBatch", "findSnapshot", "findMissingFinishedBatchIds", "insertStudentFacts",
    "findAggregateMetrics", "findDimensions", "insertSnapshot",
):
    require(method in mapper, f"analytics mapper method missing: {method}")
    require(f'id="{method}"' in xml, f"analytics mapper XML statement missing: {method}")

upper = xml.upper()
require("ROW_NUMBER()" in upper, "latest residency selection must use a window function")
for cte in ("LATEST_RESIDENCY", "ALLOCATION_SCORE", "OPTIMIZATION_SCORE", "RECOMMENDATION_FACT", "WAITLIST_FACT"):
    require(cte in upper, f"set-based analytics fact CTE missing: {cte}")
require("CASE WHEN EXISTS" not in upper, "analytics student facts must not use per-student EXISTS subqueries")
require("RESIDENCY.ID=(" not in upper.replace(" ", ""), "latest residency correlated subquery must be eliminated")
require("PARTICIPANT_COUNT" in upper and "SELF_SELECTION_COUNT" in upper and "TEAM_SELECTION_COUNT" in upper,
        "aggregate metric query must calculate multiple metrics in one scan")
require(upper.count("FROM BATCH_ANALYTICS_STUDENT_FACT") <= 3,
        "analytics metrics should not repeatedly rescan the student fact table")
require("SELECT *" not in upper, "analytics mapper must not use SELECT *")

baseline = BASELINE.read_text(encoding="utf-8")
require("BatchAnalyticsSnapshotService.java" not in baseline,
        "BatchAnalyticsSnapshotService must leave the >300-line baseline")
print("batch analytics snapshot MyBatis performance contract: OK")
