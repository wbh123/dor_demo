#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"missing required file: {path}")
    return target.read_text(encoding="utf-8")


def require(source: str, token: str, message: str) -> None:
    if token not in source:
        raise AssertionError(message)


def forbid(source: str, token: str, message: str) -> None:
    if token in source:
        raise AssertionError(message)


reset_service = read("backend-java/server/src/main/java/com/wust/dormitory/admin/StudentAccountAdminService.java")
residency_service = read("backend-java/server/src/main/java/com/wust/dormitory/residency/ResidencyService.java")
require(reset_service, "room_assignment", "complete reset must handle cross-batch residency")
require(reset_service, "residencyService.end", "complete reset must end active residency through the authoritative service")
require(residency_service, "RESIDENCY_ENDED", "residency end must preserve history")
require(reset_service, "releaseAllForStudent", "complete reset must clear Redis bed holds")
require(reset_service, "cancelActiveRoomChanges", "complete reset must cancel active room-change requests")

admin_data = read("frontend/src/views/admin/AdminDataView.vue")
forbid(admin_data, "window.scrollTo", "student editing must not jump to the top of the page")
require(admin_data, "student-edit-overlay", "student editing must use a modal overlay")
require(admin_data, "aria-labelledby=\"student-edit-title\"", "student edit dialog must be accessible")

room_list = read("frontend/src/views/student/RoomListView.vue")
forbid(room_list, "{{ room.selectionHint }}", "ROOM cards must not repeat the room-only selection hint")
require(room_list, "room-card-compact", "ROOM mode must use compact room cards")
require(room_list, ":class=\"{ 'room-card-compact': isRoomMode }\"", "compact card mode must be conditional")

root_openapi = read("backend-java/model/src/main/resources/openapi-interface.yaml")
for token in (
    "/api/v1/student/room-change/policy",
    "/api/v1/student/room-change/candidates",
    "/api/v1/student/room-change/requests",
    "/api/v1/admin/room-change/settings",
    "/api/v1/admin/room-change/requests",
    "/api/v1/admin/operations/overview",
    "/api/v1/admin/operations/health",
    "/api/v1/admin/batches/{batchId}/allocation/optimized-preview",
):
    require(root_openapi, token, f"missing OpenAPI path: {token}")

room_change_service = read("backend-java/server/src/main/java/com/wust/dormitory/roomchange/RoomChangeService.java")
for token in (
    "DISABLED", "FREE", "APPROVAL_REQUIRED", "PENDING", "APPROVED",
    "REJECTED", "EXECUTED", "executeRoomChange", "FOR UPDATE", "auditService.success",
):
    require(room_change_service, token, f"room-change service missing behavior: {token}")

room_change_controller = read("backend-java/server/src/main/java/com/wust/dormitory/roomchange/RoomChangeController.java")
require(room_change_controller, "implements RoomChangeApi", "room-change controller must implement generated API")

operations_service = read("backend-java/server/src/main/java/com/wust/dormitory/operations/OperationsService.java")
for token in ("bedUtilization", "unselectedStudents", "manualAdjustments", "redisAvailable", "slowQueryCandidates", "fairness"):
    require(operations_service, token, f"operations service missing projection: {token}")

router = read("frontend/src/router/index.ts")
for token in ("student/room-change", "admin/room-change", "admin/operations"):
    require(router, token, f"missing frontend route: {token}")

shell = read("frontend/src/layouts/AppShell.vue")
require(shell, "换寝与交换", "admin navigation must expose room-change management")
require(shell, "运营监控", "admin navigation must expose operations dashboard")
require(shell, "申请换寝", "student navigation must expose room-change requests")

print("phase2/phase3 operations contract: OK")
