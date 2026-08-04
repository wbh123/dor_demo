#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"缺少文件：{path}")
    return target.read_text(encoding="utf-8")


def require(source: str, token: str, message: str) -> None:
    if token not in source:
        raise AssertionError(message)


def forbid(source: str, token: str, message: str) -> None:
    if token in source:
        raise AssertionError(message)


setting_service = read("backend-java/server/src/main/java/com/wust/dormitory/admin/SystemSettingService.java")
for token in ('FALLBACK_WELCOME_LOCALE = "en-US"', "normalizeLocaleMessages", "normalizeLocaleTag", 'Map.of("messages", normalizedMessages)'):
    require(setting_service, token, f"欢迎语缺少语言版本能力：{token}")

welcome_service = read("backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java")
require(welcome_service, "renderedMessages", "学生欢迎信息必须返回管理员配置的全部语言版本")
require(welcome_service, 'configuration.messages().get("en-US")', "未配置外文版本必须回退管理员英文欢迎语")
forbid(welcome_service, "countryMessages", "欢迎语不得继续按国家维护")

admin_dashboard = read("frontend/src/views/admin/AdminDashboardView.vue")
for token in ("languageMessages", "addWelcomeLanguage", "removeWelcomeLanguage", "保存全部语言版本"):
    require(admin_dashboard, token, f"管理员欢迎语编辑器缺少：{token}")
forbid(admin_dashboard, "countryMessages", "管理员页面不得继续维护国家专属欢迎语")

shell = read("frontend/src/layouts/AppShell.vue")
require(shell, "'/assert/logo-only.png'", "左侧导航必须使用/assert/logo-only.png")
require(shell, "school-brand-title", "校徽右侧必须展示系统标题")
forbid(shell, "<h1>管理控制台</h1>", "管理端不得显示占用空间的管理控制台大标题")

logo = ROOT / "frontend/public/assert/logo-only.png"
if not logo.exists() or logo.stat().st_size < 100:
    raise AssertionError("frontend/public/assert/logo-only.png必须是真实可加载图片")

main_entry = read("frontend/src/main.ts")
compact_style = read("frontend/src/student-home-compact.css")
require(main_entry, "./student-home-compact.css", "学生首页紧凑布局样式必须加载")
for token in ("align-items: flex-start", "min-height: auto", "padding: 16px 18px"):
    require(compact_style, token, f"学生姓名卡片缺少紧凑左对齐规则：{token}")

openapi = read("backend-java/model/src/main/resources/openapi-interface.yaml")
for token in (
    "/api/v1/student/room-exchanges/candidates",
    "/api/v1/student/room-exchanges/{exchangeId}/respond",
    "/api/v1/admin/room-exchanges/settings",
    "/api/v1/admin/room-exchanges/{exchangeId}/approve",
):
    require(openapi, token, f"OpenAPI缺少寝室交换路径：{token}")

exchange_service = read("backend-java/server/src/main/java/com/wust/dormitory/roomexchange/RoomExchangeService.java")
for token in ("room_exchange_participant_lock", "WAITING_TARGET", "PENDING_ADMIN", "executeExchange", "FOR UPDATE", "residencyService.end", "residencyService.assign"):
    require(exchange_service, token, f"寝室交换事务缺少：{token}")

student_view = read("frontend/src/views/student/StudentRoomChangeView.vue")
for token in ("exchangeCandidates", "incomingExchanges", "submitExchange", "respondExchange", "cancelExchange"):
    require(student_view, token, f"学生端寝室交换缺少：{token}")

admin_view = read("frontend/src/views/admin/AdminRoomChangeView.vue")
for token in ("exchangeSettings", "exchangeRequests", "saveExchangeSettings", "approve", "reject"):
    require(admin_view, token, f"管理端寝室交换缺少：{token}")

print("welcome, branding and room exchange contracts: OK")
