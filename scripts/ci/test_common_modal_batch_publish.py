#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parents[2]
errors: list[str] = []


def read(path: str) -> str:
    target = ROOT / path
    if not target.exists():
        errors.append(f"missing required file: {path}")
        return ""
    content = target.read_text(encoding="utf-8")
    if target.suffix == ".vue":
        for suffix in (".logic.ts", ".template.html", ".css"):
            companion = target.with_name(f"{target.stem}{suffix}")
            if companion.exists():
                content += "\n" + companion.read_text(encoding="utf-8")
        if path == "frontend/src/views/admin/AdminBatchView.vue":
            feature_root = ROOT / "frontend/src/features/admin-batch"
            if feature_root.exists():
                for component in sorted(feature_root.rglob("*.vue")):
                    content += "\n" + component.read_text(encoding="utf-8")
                for composable in sorted(feature_root.rglob("*.ts")):
                    content += "\n" + composable.read_text(encoding="utf-8")
    return content


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


modal = read("frontend/src/components/modal/AppModal.vue")
stack = read("frontend/src/components/modal/modalStack.ts")
confirm = read("frontend/src/components/modal/AppConfirmDialog.vue")

for token in (
    '<Teleport to="body">',
    "closeOnBackdrop",
    "closeOnEscape",
    "preventClose",
    "aria-modal=\"true\"",
    "data-app-modal-scroll-region",
    "focusInitialElement",
    "onKeydown",
    "maxHeight",
):
    require(token in modal, f"AppModal missing behavior: {token}")
for token in (
    "document.body.style.overflow = 'hidden'",
    "restoreFocus",
    "isTopModal",
    "modalDepth",
    "focusin",
    "inert",
):
    require(token in stack, f"modal stack missing behavior: {token}")
for token in (
    "<AppModal",
    "confirmationWord",
    "requireReason",
    "async function submit",
    "internalError",
    "variant",
):
    require(token in confirm, f"AppConfirmDialog missing behavior: {token}")

flow = read("frontend/src/views/admin/batchPublishFlow.ts")
for state in (
    "IDLE",
    "CREATING_DRAFT",
    "SAVING_SCOPE",
    "RUNNING_PREFLIGHT",
    "WAITING_CONFIRMATION",
    "PUBLISHING",
    "SUCCEEDED",
    "FAILED",
):
    require(state in flow, f"batch publishing state machine missing state: {state}")
require("ALLOWED_TRANSITIONS" in flow and "transitionPublishFlow" in flow,
        "batch publishing state machine does not validate transitions")

batch = read("frontend/src/views/admin/AdminBatchView.vue")
require("PublishFlowState" in batch, "batch publishing does not expose an explicit state machine")
require("AppModal" in batch and "AppConfirmDialog" in batch,
        "batch scope, preflight and publish confirmation do not use shared modal components")
require("scope-filter-panel" in batch and "scope-result-summary" in batch and "scope-result-list" in batch,
        "batch scope columns do not preserve top-aligned filter and independently scrolling results")
require("saveScopeAndContinuePublish" in batch,
        "batch scope cannot continue directly through preflight and confirmation")
require("const response = await api.post<ObjectSuccessResponse>('/api/v1/admin/batches'" in batch,
        "new draft flow does not capture the real server batch id")
require("Number(created.id)" in batch,
        "new draft flow does not use the server returned batch id")
require("resetScopeDialog()\n      await load()\n      if (continuePublish)" not in batch,
        "batch scope still closes before preflight and publish confirmation")
require("reconcilePublishedState" in batch,
        "publish timeout does not reconcile the server truth")

lifecycle = read("backend-java/server/src/main/java/com/wust/dormitory/admin/BatchLifecycleService.java")
require("currentStatus.equals(targetStatus)" in lifecycle,
        "server batch status transition is not idempotent for repeated publish requests")

if errors:
    print("Common modal and batch publish contract failed:", file=sys.stderr)
    for error in errors:
        print(f"- {error}", file=sys.stderr)
    raise SystemExit(1)

print("Common modal and batch publish contract passed")
