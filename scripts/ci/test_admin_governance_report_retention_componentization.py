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
report = read("frontend/src/features/admin-governance/composables/useReportWorkspace.ts")
report_panel = read("frontend/src/features/admin-governance/components/ReportExportPanel.vue")
retention = read("frontend/src/features/admin-governance/composables/useDataRetention.ts")
retention_panel = read("frontend/src/features/admin-governance/components/DataRetentionPanel.vue")

for token in ("ReportExportPanel", "useReportWorkspace", "DataRetentionPanel", "useDataRetention"):
    require(view, token, f"治理路由页面缺少组件化入口：{token}")
for endpoint in (
    "/api/v1/admin/governance/reports/metadata",
    "/api/v1/admin/governance/reports/templates",
    "/api/v1/admin/governance/reports/export",
    "/api/v1/admin/governance/retention/policy",
    "/api/v1/admin/governance/retention/statistics",
    "/api/v1/admin/governance/retention/simulate",
    "/api/v1/admin/governance/retention/preflight",
):
    forbid(view, endpoint, f"治理路由页面不得直接调用接口：{endpoint}")
forbid(view, "api/client", "治理路由页面完成拆分后不得直接调用接口客户端")

for endpoint in (
    "/api/v1/admin/governance/reports/metadata",
    "/api/v1/admin/governance/reports/templates",
    "/api/v1/admin/governance/reports/export",
):
    require(report, endpoint, f"报表组合函数缺少接口：{endpoint}")
for token in ("metadata", "definition", "reason", "busy", "error", "message", "updateDefinition"):
    require(report, token, f"报表组合函数缺少状态或动作：{token}")
forbid(report_panel, "api/client", "报表展示组件不得直接调用接口")
require(report_panel, "ReportBuilder", "报表面板必须复用报表构建器")
require(report_panel, "ExportTaskPanel", "报表面板必须保留异步导出任务")
require(report_panel, ':model-value="definition"', "报表构建器必须使用显式 model-value")

for endpoint in (
    "/api/v1/admin/governance/retention/policy",
    "/api/v1/admin/governance/retention/statistics",
    "/api/v1/admin/governance/retention/simulate",
    "/api/v1/admin/governance/retention/preflight",
):
    require(retention, endpoint, f"数据保留组合函数缺少接口：{endpoint}")
for token in ("policy", "statistics", "simulation", "busy", "error", "message", "preflight"):
    require(retention, token, f"数据保留组合函数缺少状态或动作：{token}")
forbid(retention_panel, "api/client", "数据保留展示组件不得直接调用接口")
require(retention_panel, "emit('confirm-preflight')", "数据保留面板必须通过事件打开确认流程")

if len(view.splitlines()) > 260:
    raise AssertionError("治理路由页面仍然过大，应仅保留标签、状态编排和确认弹窗")

print("admin governance report and retention componentization contract: OK")
