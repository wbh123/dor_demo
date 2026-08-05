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


modal = read("frontend/src/components/modal/AppModal.vue")
stack = read("frontend/src/components/modal/modalStack.ts")
confirm = read("frontend/src/components/modal/AppConfirmDialog.vue")

checks = {
    "opening registers modal": "registerModal(id, root.value)" in modal,
    "closing unregisters and restores focus": "await unregisterModal(id)" in modal and "restoreFocus" in stack,
    "escape closes only top modal": "event.key === 'Escape'" in modal and "isTopModal(id)" in modal,
    "backdrop can be disabled": "props.closeOnBackdrop" in modal,
    "focus is trapped": "event.key !== 'Tab'" in modal and "focusableElements(panel.value)" in modal,
    "body scrolling is locked": "document.body.style.overflow = 'hidden'" in stack,
    "modal content scrolls independently": "overflow:auto" in modal and "data-app-modal-scroll-region" in modal,
    "nested modal depth is explicit": "modalDepth(id)" in modal and "stack.at(-1)" in stack,
    "mobile safe area is supported": "env(safe-area-inset-bottom)" in modal,
    "async confirmation catches errors": "await props.action(payload)" in confirm and "catch (cause)" in confirm,
    "confirmation word is required": "typedConfirmation.value.trim() === props.confirmationWord" in confirm,
    "reason can be required": "!props.requireReason || reason.value.trim().length >= 2" in confirm,
}
failed = [name for name, passed in checks.items() if not passed]
if failed:
    raise AssertionError("modal behavior contracts failed: " + ", ".join(failed))
print("Modal component behavior contracts passed")
