#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
errors: list[str] = []


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        errors.append(f"missing required file: {path}")
        return ""
    return target.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


snapshot = (
    read("backend-java/server/src/main/java/com/wust/dormitory/analytics/BatchAnalyticsSnapshotService.java")
    + read("backend-java/server/src/main/resources/mapper/analytics/BatchAnalyticsSnapshotMapper.xml")
)
historical = read("backend-java/server/src/main/java/com/wust/dormitory/analytics/HistoricalAnalyticsService.java")
lifecycle = (
    read("backend-java/server/src/main/java/com/wust/dormitory/admin/BatchLifecycleService.java")
    + read("backend-java/server/src/main/resources/mapper/admin/BatchLifecycleMapper.xml")
)
audit = read("backend-java/server/src/main/java/com/wust/dormitory/audit/AuditQueryService.java")
retention = read("backend-java/server/src/main/java/com/wust/dormitory/retention/DataRetentionQueryService.java")

for forbidden in ("recommendation_log", "assignment_source"):
    require(forbidden not in snapshot, f"snapshot service still references nonexistent schema object: {forbidden}")

recommendation_match = re.search(
    r"recommendation_fact\s+AS\s*\((?P<body>.*?)\)\s*,\s*room_change_fact",
    snapshot,
    re.IGNORECASE | re.DOTALL,
)
recommendation_sql = recommendation_match.group("body") if recommendation_match else ""
require(bool(recommendation_sql), "recommendation fact CTE is missing from immutable snapshot SQL")
for pattern, label in (
    (r"\brequest\.request_status\b", "request.request_status"),
    (r"\brequest\.completed_at\b", "request.completed_at"),
    (r"\brequest_status\s*=\s*'SUCCEEDED'", "request_status='SUCCEEDED'"),
):
    require(re.search(pattern, recommendation_sql) is None,
            f"snapshot service still references nonexistent recommendation schema object: {label}")

require("batch_analytics_student_fact" in snapshot,
        "finished batch analytics do not create immutable student facts")
require("student_recommendation_request" in recommendation_sql
        and "request.created_at" in recommendation_sql
        and "response_json" in recommendation_sql,
        "recommendation adoption is not derived from V27 recommendation requests")
require("allocation_run_result" in snapshot and "allocation_optimization_candidate" in snapshot,
        "match scores are not derived from existing allocation result chains")
require("assignment_method" in snapshot and "match_score" in snapshot,
        "snapshot metrics do not use the real assignment method and fact score fields")
require("room_change_request" in snapshot and "source_residency_id" in snapshot,
        "room-change metrics are not joined through the actual residency source")
require("room_exchange_request" in snapshot and "initiator_residency_id" in snapshot,
        "exchange metrics are not joined through the actual participant residencies")
require("student_status" not in snapshot,
        "snapshot service still references nonexistent student.student_status")
require("housing_eligibility" not in snapshot,
        "snapshot service still references the student.housing_eligibility column removed by V3")
require("operation_anomaly WHERE batch_id" not in snapshot,
        "snapshot service still assumes operation_anomaly has a batch_id column")

require("batch_analytics_student_fact" in historical,
        "historical filters still read mutable current student and residency tables")
require("assignment.room_id" not in historical,
        "historical analytics still joins the nonexistent bed_assignment.room_id")
require("PRIVACY_THRESHOLD" in historical and "sample_size" in historical,
        "small-sample privacy suppression is not based on immutable facts")
require("selection_batch" in lifecycle
        and "finished_at" in lifecycle
        and "COALESCE(finished_at" in lifecycle,
        "finished batch timestamp is not captured for immutable completion duration")
require("audit.ip_address=:networkAddress" in audit and "audit.ip_address AS network_address" in audit,
        "advanced audit does not map the existing ip_address column")
require("audit.network_address" not in audit,
        "advanced audit still references a nonexistent network_address column")
require("FROM room_exchange_request WHERE request_status NOT IN" in retention,
        "retention protection does not use the V23 room exchange request_status column")
require("exchange_status" not in retention,
        "retention protection still references nonexistent room_exchange_request.exchange_status")
require("student_status" not in retention,
        "retention protection still references nonexistent student.student_status")
require("housing_eligibility" not in retention,
        "retention protection still references the student.housing_eligibility column removed by V3")
require("SELECT COUNT(*) FROM student" in retention,
        "retention protection does not conservatively protect all current student records")

if errors:
    print("Governance schema alignment contract failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)
print("Governance schema alignment contract passed")
