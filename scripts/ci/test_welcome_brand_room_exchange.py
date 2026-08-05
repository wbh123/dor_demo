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
    "normalizeCountryMessages",
    'result.put("countryMessages", configuration.countryMessages())',
    "DEFAULT_MESSAGES.get(FALLBACK_WELCOME_LOCALE)",
):
    require(setting_service, token, f"welcome setting must support base and country messages: {token}")

welcome_service = read("backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java")
require(welcome_service, "renderedMessages", "student welcome must return administrator-configured language versions")
require(welcome_service, "configuration.messages().forEach", "student welcome must render every configured language through the canonical messages object")
require(welcome_service, "configuration.countryMessages().get(countryCode)", "student welcome must prefer a matching country or region message")
require(welcome_service, "countryTemplate == null || countryTemplate.isBlank() ? message : countryTemplate", "country or region message must override the language content when configured")
forbid(welcome_service, "data.setMessage(", "student welcome must not restore the removed single-message compatibility field")

admin_dashboard = read("frontend/src/views/admin/AdminDashboardView.vue")
for token in (
    "CountryWelcomeEditor",
    "汉语",
    "英语",
    "countryMessages",
    "其他国家或地区",
    "未配置时自动使用英语欢迎语",
):
    require(admin_dashboard, token, f"administrator welcome editor missing behavior: {token}")
for token in ("newLocale", "languageMessages", "删除语言", "localeCode }}", "<strong>美国</strong>", "美国卡片"):
    forbid(admin_dashboard, token, f"welcome editor must not expose a legacy language or United States presentation workflow: {token}")

country_editor = read("frontend/src/components/admin/CountryWelcomeEditor.vue")
for token in ("已配置国家或地区", "添加国家或地区", "availableCountries", "configuredCodes", "WelcomeMessageEditor"):
    require(country_editor, token, f"shared country welcome editor missing behavior: {token}")
require(country_editor, "未配置国家或地区自动使用英语欢迎语", "country editor must describe the English fallback without presenting the United States as a language")

shell = read("frontend/src/layouts/AppShell.vue")
require(shell, "${publicBase}assert/logo-only.png", "navigation must use a BASE_URL-safe public emblem asset")
require(shell, "import.meta.env.BASE_URL", "navigation emblem must support non-root Vite deployment")
require(shell, "school-brand-title", "system title must be displayed to the right of the emblem")
require(shell, "logo-safe-layer", "emblem must be rendered above decorative overlays")
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
for token in ("ThreeStateToggle", "roomChangeMode", "roomExchangeMode", "savePolicy('change')", "savePolicy('exchange')"):
    require(admin_room_change, token, f"admin room-change page missing shared three-state management: {token}")

feature_codes = read("backend-java/server/src/main/java/com/wust/dormitory/subscription/FeatureCodes.java")
for token in ("P3_ROOM_EXCHANGE_REQUEST", "P3_ROOM_EXCHANGE_REVIEW", "P3_ROOM_EXCHANGE_EXECUTE"):
    require(feature_codes, token, f"missing feature code: {token}")

print("country welcome, branding and room-exchange contract: OK")
