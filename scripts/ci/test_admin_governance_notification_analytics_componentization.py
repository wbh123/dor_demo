#!/usr/bin/env python3
from pathlib import Path
import sys

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


view = read("frontend/src/views/admin/AdminGovernanceView.vue")
notification = read("frontend/src/features/admin-governance/composables/useNotificationCenter.ts")
notification_panel = read("frontend/src/features/admin-governance/components/NotificationCenterPanel.vue")
analytics = read("frontend/src/features/admin-governance/composables/useHistoricalAnalytics.ts")
analytics_panel = read("frontend/src/features/admin-governance/components/HistoricalAnalyticsPanel.vue")

for token in ("NotificationCenterPanel", "useNotificationCenter", "HistoricalAnalyticsPanel", "useHistoricalAnalytics"):
    require(view, token, f"治理路由页面缺少组件化入口：{token}")

for endpoint in (
    "/api/v1/admin/governance/notifications/templates",
    "/api/v1/admin/governance/notifications/preflight",
    "/api/v1/admin/governance/notifications/schedule",
    "/api/v1/admin/governance/notifications/status",
    "/api/v1/admin/governance/analytics/definitions",
    "/api/v1/admin/governance/analytics/",
):
    forbid(view, endpoint, f"治理路由页面不得直接调用接口：{endpoint}")

for endpoint in (
    "/api/v1/admin/governance/notifications/templates",
    "/api/v1/admin/governance/notifications/templates/revisions",
    "/api/v1/admin/governance/notifications/preflight",
    "/api/v1/admin/governance/notifications/schedule",
    "/api/v1/admin/governance/notifications/status",
):
    require(notification, endpoint, f"通知中心组合函数缺少接口：{endpoint}")
for token in ("busy", "error", "message", "templateDraft", "recipientCriteria", "sendNotification"):
    require(notification, token, f"通知中心组合函数缺少独立状态或动作：{token}")

forbid(notification_panel, "api/client", "通知中心展示组件不得直接调用接口")
require(notification_panel, "NotificationTemplateEditor", "通知中心面板必须复用模板编辑器")
require(notification_panel, "RecipientSelector", "通知中心面板必须复用接收范围组件")
require(notification_panel, "emit('confirm-send')", "通知中心面板必须通过事件打开确认流程")

require(analytics, "/api/v1/admin/governance/analytics/definitions", "历史分析组合函数必须加载指标定义")
require(analytics, "/api/v1/admin/governance/analytics/", "历史分析组合函数必须执行分析查询")
for token in ("modes", "mode", "filters", "definitions", "items", "privacy", "busy", "error", "run"):
    require(analytics, token, f"历史分析组合函数缺少状态或动作：{token}")

forbid(analytics_panel, "api/client", "历史分析展示组件不得直接调用接口")
require(analytics_panel, "AnalyticsFilterBar", "历史分析面板必须复用筛选组件")
require(analytics_panel, "MetricDefinitionPopover", "历史分析面板必须保留指标口径说明")
require(analytics_panel, "emit('run')", "历史分析面板必须通过事件执行查询")

print("admin governance notification and analytics componentization contract: OK")
