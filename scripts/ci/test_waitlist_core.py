#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"missing required file: {path}")
    text = target.read_text(encoding="utf-8")
    if target.suffix == ".vue":
        stem = target.with_suffix("")
        for suffix in (".logic.ts", ".template.html", ".css"):
            companion = Path(str(stem) + suffix)
            if companion.is_file():
                text += "
" + companion.read_text(encoding="utf-8")
    return text

def require(source: str, token: str, message: str) -> None:
    if token not in source:
        raise AssertionError(message)


openapi = read("backend-java/model/src/main/resources/openapi-interface.yaml")
for token in (
    "/api/v1/student/waitlist/policy",
    "/api/v1/student/waitlist/candidates",
    "/api/v1/student/waitlist/entries",
    "/api/v1/student/waitlist/entries/{entryId}/withdraw",
    "/api/v1/student/waitlist/offers/{offerId}/accept",
    "/api/v1/student/waitlist/offers/{offerId}/reject",
    "/api/v1/admin/waitlist/settings",
    "/api/v1/admin/waitlist/entries",
    "/api/v1/admin/waitlist/entries/{entryId}/priority",
    "/api/v1/admin/waitlist/entries/{entryId}/offer",
    "/api/v1/admin/waitlist/entries/{entryId}/assign",
    "/api/v1/admin/waitlist/scan",
):
    require(openapi, token, f"missing waitlist OpenAPI path: {token}")

fragment = read("backend-java/model/src/main/resources/waitlist/openapi-waitlist.yaml")
for token in (
    "WaitlistJoinRequest",
    "WaitlistPolicyRequest",
    "WaitlistPriorityRequest",
    "WaitlistActionRequest",
    "offerTtlMinutes",
    "scanBatchSize",
):
    require(fragment, token, f"missing waitlist schema token: {token}")

service = read("backend-java/server/src/main/java/com/wust/dormitory/waitlist/WaitlistService.java")
for token in (
    "FOR UPDATE",
    "WAITLIST_ASSIGNMENT",
    "expireOffers",
    "scanAvailableResources",
    "createOffer",
    "directAssign",
    "student_notification",
    "WAITLIST_OFFERED",
):
    require(service, token, f"missing waitlist service behavior: {token}")

scheduler = read("backend-java/server/src/main/java/com/wust/dormitory/waitlist/WaitlistScheduler.java")
require(scheduler, "@Scheduled", "waitlist scan must run on a schedule")
require(scheduler, "expireOffers", "scheduler must expire offers")
require(scheduler, "scanAvailableResources", "scheduler must scan available resources")

controller = read("backend-java/server/src/main/java/com/wust/dormitory/waitlist/WaitlistController.java")
require(controller, "implements WaitlistApi", "waitlist controller must implement generated API")

feature_codes = read("backend-java/server/src/main/java/com/wust/dormitory/subscription/FeatureCodes.java")
for token in ("P3_WAITLIST_REQUEST", "P3_WAITLIST_MANAGE", "P3_WAITLIST_ASSIGN", "P3_WAITLIST_HISTORY"):
    require(feature_codes, token, f"missing waitlist feature code: {token}")

student_view = read("frontend/src/views/student/StudentWaitlistView.vue")
for token in ("joinWaitlist", "withdrawEntry", "acceptOffer", "rejectOffer"):
    require(student_view, token, f"student waitlist UI missing: {token}")

admin_view = read("frontend/src/views/admin/AdminWaitlistView.vue")
for token in ("saveSettings", "updatePriority", "createOffer", "directAssign", "scanWaitlist"):
    require(admin_view, token, f"admin waitlist UI missing: {token}")

router = read("frontend/src/router/index.ts")
require(router, "student/waitlist", "student waitlist route missing")
require(router, "admin/waitlist", "admin waitlist route missing")

shell = read("frontend/src/layouts/AppShell.vue")
require(shell, "候补补位", "student waitlist navigation missing")
require(shell, "候补管理", "admin waitlist navigation missing")

print("waitlist core contract: OK")
