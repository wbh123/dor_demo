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
audit = read("frontend/src/features/admin-governance/composables/useAuditSearch.ts")
exports = read("frontend/src/features/admin-governance/composables/useGovernanceExports.ts")
panel = read("frontend/src/features/admin-governance/components/AuditSearchPanel.vue")

require(view, "AuditSearchPanel", "治理页面必须使用独立审计面板")
require(view, "useAuditSearch", "治理页面必须使用审计领域组合函数")
require(view, "useGovernanceExports", "治理页面必须使用导出任务组合函数")
for endpoint in (
    "/api/v1/admin/governance/audit/query",
    "/api/v1/admin/governance/audit/export",
    "/api/v1/admin/governance/exports",
):
    forbid(view, endpoint, f"治理路由页面不得直接调用接口：{endpoint}")

require(audit, "/api/v1/admin/governance/audit/query", "审计查询接口必须由审计组合函数管理")
require(audit, "/api/v1/admin/governance/audit/export", "审计导出接口必须由审计组合函数管理")
for token in ("busy", "error", "message", "selectedAudit", "requestExport"):
    require(audit, token, f"审计组合函数缺少独立状态或动作：{token}")

require(exports, "/api/v1/admin/governance/exports", "导出任务接口必须由导出组合函数管理")
require(exports, "setInterval", "导出组合函数必须管理进行中任务轮询")
require(exports, "onBeforeUnmount", "导出组合函数必须在组件卸载时停止轮询")
for token in ("tasks", "busy", "error", "load", "cancel"):
    require(exports, token, f"导出组合函数缺少状态或动作：{token}")

forbid(panel, "api/client", "审计展示组件不得直接调用接口")
require(panel, "AuditFilterBar", "审计面板必须保留现有筛选组件")
require(panel, "ExportTaskPanel", "审计面板必须保留异步导出任务列表")
require(panel, "emit('search')", "审计面板必须通过事件请求查询")
require(panel, "emit('request-export'", "审计面板必须通过事件请求导出")

print("admin governance audit componentization contract: OK")
