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
scope = read("frontend/src/features/admin-batch/components/BatchScopeDialog.vue")
preflight = read("frontend/src/features/admin-batch/components/BatchPreflightDialog.vue")

for token in ("BatchScopeDialog", "BatchPreflightDialog"):
    require(view, token, f"选寝批次页面必须导入组件：{token}")
    require(template, token, f"选寝批次模板必须使用组件：{token}")

forbid(template, ':open="scopeDialog"', "参与范围弹窗不得继续内联在大型模板中")
forbid(template, ':open="Boolean(preflightBatch && roomPreflight)"', "发布预检弹窗不得继续内联在大型模板中")

forbid(scope, "api/client", "参与范围展示组件不得直接调用接口")
forbid(preflight, "api/client", "发布预检展示组件不得直接调用接口")
for token in (
    "AppModal",
    "selectedStudentIds",
    "selectedRoomIds",
    "filteredStudents",
    "filteredRooms",
    "emit('save')",
    "emit('save-and-publish')",
):
    require(scope, token, f"参与范围组件缺少属性或事件：{token}")
for token in (
    "AppModal",
    "preflightBatch",
    "roomPreflight",
    "preflightRooms",
    "preflightMissingSteps",
    "emit('reopen-scope')",
):
    require(preflight, token, f"发布预检组件缺少属性或事件：{token}")

baseline = read("scripts/ci/frontend_modularization_baseline.json")
forbid(baseline, '"frontend/src/views/admin/AdminBatchView.vue": 1035', "拆分后必须继续下调批次页面规模基线")

print("admin batch scope and preflight componentization contract: OK")
