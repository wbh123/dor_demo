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


view = read("frontend/src/views/admin/AdminBatchView.vue")
template = read("frontend/src/views/admin/AdminBatchView.template.html")
batch_list = read("frontend/src/features/admin-batch/components/BatchListPanel.vue")
publish_dialog = read("frontend/src/features/admin-batch/components/BatchPublishConfirmationDialog.vue")
allocation_dialog = read("frontend/src/features/admin-batch/components/BatchAllocationPreviewDialog.vue")

for token in ("BatchListPanel", "BatchPublishConfirmationDialog", "BatchAllocationPreviewDialog"):
    require(view, token, f"选寝批次页面必须导入组件：{token}")
    require(template, token, f"选寝批次模板必须使用组件：{token}")

forbid(template, 'v-for="batch in batches"', "批次列表不得继续内联在大型模板中")
forbid(template, "<AppConfirmDialog", "发布确认弹窗不得继续内联在大型模板中")
forbid(template, '<AppModal :open="Boolean(allocationPreview)"', "统一分配预演不得继续内联在大型模板中")

for component, name in (
    (batch_list, "批次列表"),
    (publish_dialog, "发布确认"),
    (allocation_dialog, "统一分配预演"),
):
    forbid(component, "api/client", f"{name}展示组件不得直接调用接口")

for token in (
    "batches",
    "publishFlowBusy",
    "nextActions",
    "emit('open-scope'",
    "emit('preflight'",
    "emit('open-copy'",
    "emit('change-status'",
    "emit('preview-allocation'",
    "emit('download'",
):
    require(batch_list, token, f"批次列表组件缺少属性或事件：{token}")

for token in (
    "AppConfirmDialog",
    "publishConfirmation",
    "publishPreflightSnapshot",
    "selectedStudentCount",
    "confirm-text=\"确认发布\"",
    "emit('close')",
):
    require(publish_dialog, token, f"发布确认组件缺少属性或行为：{token}")

for token in (
    "AppModal",
    "allocationPreview",
    "allocationSummary",
    "unassignedStudents",
    "emit('commit')",
    "emit('close')",
):
    require(allocation_dialog, token, f"统一分配预演组件缺少属性或事件：{token}")

baseline = read("scripts/ci/frontend_modularization_baseline.json")
forbid(baseline, '"frontend/src/views/admin/AdminBatchView.vue": 994', "拆分后必须继续下调批次页面规模基线")

print("admin batch list, publish and allocation componentization contract: OK")
