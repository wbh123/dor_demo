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


setting_service = read("backend-java/server/src/main/java/com/wust/dormitory/admin/SystemSettingService.java")
for token in (
    'FALLBACK_WELCOME_LOCALE = "en-US"',
    "normalizeLocaleMessages",
    "normalizeLocaleTag",
    "messages().get(FALLBACK_WELCOME_LOCALE)",
):
    require(setting_service, token, f"welcome setting must support administrator-managed locale messages: {token}")
forbid(setting_service, 'List.of("zh-CN", "en-US")', "welcome languages must not be hard-coded to only Chinese and English")

welcome_service = read("backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java")
require(welcome_service, "renderedMessages", "student welcome must return all administrator-configured language versions")
require(welcome_service, 'configuration.messages().get("en-US")', "foreign-language fallback must use the administrator English message")
require(welcome_service, "is intentionally no longer executed", "legacy country selection must be documented as disabled")
forbid(welcome_service, "countryMessages().forEach", "welcome selection must not iterate country-specific copies")

admin_dashboard = read("frontend/src/views/admin/AdminDashboardView.vue")
for token in ("welcomeLanguageOptions", "addWelcomeLanguage", "removeWelcomeLanguage", "languageMessages"):
    require(admin_dashboard, token, f"administrator welcome editor missing language management behavior: {token}")
require(admin_dashboard, "countryMessages configuration is migrated", "legacy editor migration must remain explicit")

shell = read("frontend/src/layouts/AppShell.vue")
require(shell, "'/assert/logo-only.png'", "navigation must use the fixed /assert/logo-only.png asset")
require(shell, "school-brand-title", "system title must be displayed to the right of the emblem")
forbid(shell, "logo-title-right.png", "navigation must not reference the missing combined-logo asset")
forbid(shell, "<h1>管理控制台</h1>", "the administration console headline must be removed")

logo = ROOT / "frontend/public/assert/logo-only.png"
if not logo.exists() or logo.stat().st_size < 100:
    raise AssertionError("frontend/public/assert/logo-only.png must be a real static image asset")

student_home_style = read("frontend/src/student-home-compact.css")
main_entry = read("frontend/src/main.ts")
require(main_entry, "./student-home-compact.css", "student home compact stylesheet must be loaded")
for token in (
    ".welcome-card.compact-home-top-card",
    "align-items: flex-start",
    "min-height: auto",
    "padding: 16px 18px",
):
    require(student_home_style, token, f"student identity card must be compact and left aligned: {token}")

root_openapi = read("backend-java/model/src/main/resources/openapi-interface.yaml")
for token in (
    "/api/v1/student/room-exchanges/candidates",
    "/api/v1/student/room-exchanges",
    "/api/v1/student/room-exchanges/{exchangeId}/respond",
    "/api/v1/admin/room-exchanges/settings",
    "/api/v1/admin/room-exchanges/{exchangeId}/approve",
):
    require(root_openapi, token, f"missing room-exchange OpenAPI path: {token}")

exchange_openapi = read("backend-java/model/src/main/resources/roomexchange/openapi-room-exchange.yaml")
for token in (
    "WAITING_TARGET",
    "PENDING_ADMIN",
    "MUTUAL_CONFIRMATION",
    "APPROVAL_REQUIRED",
    "RoomExchangeRespondRequest",
):
    require(exchange_openapi, token, f"room-exchange contract missing state or request type: {token}")

exchange_service = read("backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeService.java")
for token in (
    "room_exchange_participant_lock",
    "WAITING_TARGET",
    "PENDING_ADMIN",
    "executeExchange",
    "FOR UPDATE",
    "residencyService.end",
    "residencyService.assign",
    "MUTUAL_CONFIRMATION",
    "APPROVAL_REQUIRED",
):
    require(exchange_service, token, f"room-exchange service missing transactional behavior: {token}")

exchange_controller = read("backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeController.java")
require(exchange_controller, "implements RoomExchangeApi", "room-exchange controller must implement the generated OpenAPI interface")

student_room_change = read("frontend/src/views/student/StudentRoomChangeView.vue")
for token in ("exchangeCandidates", "incomingExchanges", "submitExchange", "respondExchange"):
    require(student_room_change, token, f"student room-change page missing exchange behavior: {token}")

admin_room_change = read("frontend/src/views/admin/AdminRoomChangeView.vue")
for token in ("exchangePolicy", "exchangeRequests", "approveExchange", "rejectExchange"):
    require(admin_room_change, token, f"admin room-change page missing exchange management behavior: {token}")

feature_codes = read("backend-java/server/src/main/java/com/wust/dormitory/subscription/FeatureCodes.java")
for token in ("P3_ROOM_EXCHANGE_REQUEST", "P3_ROOM_EXCHANGE_REVIEW", "P3_ROOM_EXCHANGE_EXECUTE"):
    require(feature_codes, token, f"missing feature code: {token}")

print("welcome, branding and room-exchange contract: OK")
