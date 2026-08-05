#!/usr/bin/env python3
from __future__ import annotations

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


snapshot = read("backend-java/server/src/main/java/com/wust/dormitory/analytics/BatchAnalyticsSnapshotService.java")
historical = read("backend-java/server/src/main/java/com/wust/dormitory/analytics/HistoricalAnalyticsService.java")
lifecycle = read("backend-java/server/src/main/java/com/wust/dormitory/admin/BatchLifecycleService.java")
audit = read("backend-java/server/src/main/java/com/wust/dormitory/audit/AuditQueryService.java")

for forbidden in (
    "recommendation_log", "assignment_source", "request.request_status",
    "request.completed_at", "request_status='SUCCEEDED'",
):
    require(forbidden not in snapshot, f"snapshot service still references nonexistent schema object: {forbidden}")
require("batch_analytics_student_fact" in snapshot,
        "finished batch analytics do not create immutable student facts")
require("student_recommendation_request" in snapshot and "request.created_at" in snapshot and "response_json" in snapshot,
        "recommendation adoption is not derived from V27 recommendation requests")
require("allocation_run_result" in snapshot and "allocation_optimization_candidate" in snapshot,
        "match scores are not derived from existing allocation result chains")
require("assignment_method" in snapshot and "match_score" in snapshot,
        "snapshot metrics do not use the real assignment method and fact score fields")
require("room_change_request" in snapshot and "source_residency_id" in snapshot,
        "room-change metrics are not joined through the actual residency source")
require("room_exchange_request" in snapshot and "initiator_residency_id" in snapshot,
        "exchange metrics are not joined through the actual participant residencies")

require("batch_analytics_student_fact" in historical,
        "historical filters still read mutable current student and residency tables")
require("assignment.room_id" not in historical,
        "historical analytics still joins the nonexistent bed_assignment.room_id")
require("PRIVACY_THRESHOLD" in historical and "sample_size" in historical,
        "small-sample privacy suppression is not based on immutable facts")
require("selection_batch SET finished_at" in lifecycle,
        "finished batch timestamp is not captured for immutable completion duration")
require("audit.ip_address=:networkAddress" in audit and "audit.ip_address AS network_address" in audit,
        "advanced audit does not map the existing ip_address column")
require("audit.network_address" not in audit,
        "advanced audit still references a nonexistent network_address column")

if errors:
    print("Governance schema alignment contract failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)
print("Governance schema alignment contract passed")
