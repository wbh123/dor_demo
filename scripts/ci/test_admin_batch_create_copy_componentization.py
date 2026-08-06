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
creation = read("frontend/src/features/admin-batch/components/BatchCreationPanel.vue")
copy_dialog = read("frontend/src/features/admin-batch/components/BatchCopyDialog.vue")

for token in ("BatchCreationPanel", "BatchCopyDialog"):
    require(view, token, f"选寝批次页面必须导入组件：{token}")
    require(template, token, f"选寝批次模板必须使用组件：{token}")

forbid(template, 'class="batch-create-form"', "创建批次表单不得继续内联在大型模板中")
forbid(template, '<AppModal :open="copyDialog"', "复制批次弹窗不得继续内联在大型模板中")

forbid(creation, "api/client", "创建批次展示组件不得直接调用接口")
forbid(copy_dialog, "api/client", "复制批次展示组件不得直接调用接口")
for token in ("ruleTemplates", "bedModeAuthorized", "ruleTemplateSummary", "emit('submit')"):
    require(creation, token, f"创建批次组件缺少属性或事件：{token}")
for token in ("AppModal", "copySource", "copyForm", "emit('submit')", "emit('close')"):
    require(copy_dialog, token, f"复制批次组件缺少属性或事件：{token}")

baseline = read("scripts/ci/frontend_modularization_baseline.json")
forbid(baseline, '"frontend/src/views/admin/AdminBatchView.vue": 1054', "拆分后必须下调批次页面规模基线")

print("admin batch create and copy componentization contract: OK")
