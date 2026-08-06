#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        raise AssertionError(f"missing required file: {path}")
    return target.read_text(encoding="utf-8")


view = read("frontend/src/views/admin/AdminOperationsView.vue")
for token in (
    "useAdminOperationsPage",
    "OperationsMetricsGrid",
    "OperationsHealthSummary",
    "FairnessPreviewPanel",
):
    assert token in view, f"运营页面缺少组件化入口：{token}"
assert "api.get" not in view, "路由页面不得继续直接编排运营接口"
assert len(view.splitlines()) <= 100, "运营路由页面应保持轻量"

composable = read("frontend/src/features/admin-operations/composables/useAdminOperationsPage.ts")
for token in ("load", "loadPreview", "selectableBatches", "previewLoading"):
    assert token in composable, f"运营组合函数缺少状态或动作：{token}"

for component in (
    "OperationsMetricsGrid.vue",
    "OperationsHealthSummary.vue",
    "FairnessPreviewPanel.vue",
):
    source = read(f"frontend/src/features/admin-operations/components/{component}")
    assert "../../api/client" not in source and "api/client" not in source, f"{component} 不得直接请求接口"

print("admin operations componentization contract: OK")
