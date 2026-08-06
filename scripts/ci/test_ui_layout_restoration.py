#!/usr/bin/env python3
from __future__ import annotations

# Behavioral contract for the requested room-layout, batch-form and shared-modal restoration.
import sys
from pathlib import Path

root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[2])
errors: list[str] = []


def read(path: str) -> str:
    source = root / path
    if not source.exists():
        raise AssertionError(f"missing required file: {path}")
    return source.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


layout = read("frontend/src/components/admin/RoomLayoutEditor.vue")
require("aspect-ratio:19/9" in layout.replace(" ", ""),
        "bed cards are not fixed to the requested 1.9:0.9 rounded-rectangle ratio")
require("layout-relative-reference" not in layout,
        "the two redundant room-layout reference bubbles are still rendered")
for label in ("旋转90°", "上下铺", "上床下桌", "单人床"):
    require(label in layout, f"bed card is missing the {label} action")
require(layout.count('@click.stop="setType') == 3 and layout.count('@click.stop="rotate(unit)"') == 1,
        "each editable bed card must expose exactly one rotate action and three bed-type actions")
for forbidden in ("靠门", "靠窗", "前移", "后移", "恢复标准2×2布局"):
    require(forbidden not in layout, f"room layout editor still exposes the forbidden {forbidden} action")
require("unit-list" not in layout and "type-buttons" not in layout,
        "bed-type actions are still separated from the bed card")

batch_template = read("frontend/src/views/admin/AdminBatchView.template.html")
batch_css = read("frontend/src/views/admin/AdminBatchView.css")
batch_creation = read("frontend/src/features/admin-batch/components/BatchCreationPanel.vue")
batch_form_markup = batch_template + "\n" + batch_creation
batch_form_styles = batch_css + "\n" + batch_creation
require('<div class="separation-switch">' in batch_form_markup,
        "domestic/international separation selector still uses the malformed label/button nesting")
require("appearance:none" in batch_form_styles.replace(" ", "") and "cursor:pointer" in batch_form_styles.replace(" ", ""),
        "selection mode cards are missing explicit button reset and pointer interaction styles")
require(".rule-summary" in batch_form_styles and "align-self:end" in batch_form_styles.replace(" ", "")
        and "min-height:" in batch_form_styles,
        "rule summary is not vertically aligned with the date selector row")
require("height:min(60vh,620px)" not in batch_css.replace(" ", ""),
        "scope modal still forces the regressed fixed-height layout")
require("max-height:440px" in batch_css.replace(" ", ""),
        "scope result lists do not restore the original independent scrolling height")
require("justify-content:flex-start" in batch_css.replace(" ", ""),
        "scope columns are not explicitly top-aligned")

modal = read("frontend/src/components/modal/AppModal.vue")
confirm = read("frontend/src/components/modal/AppConfirmDialog.vue")
require("'compact'" in modal and "app-modal--compact" in modal,
        "common modal does not provide the compact confirmation-dialog size")
require("width:min(1180px,100%)" in modal.replace(" ", "")
        and "width:min(980px,100%)" in modal.replace(" ", ""),
        "common modal large and wide sizes do not preserve the original page dimensions")
require("box-sizing:border-box" in modal.replace(" ", ""),
        "common modal surfaces do not use stable border-box sizing")
require('size="compact"' in confirm,
        "shared confirmation dialog is not using the restored compact surface")

if errors:
    print("\n".join(f"- {error}" for error in errors))
    raise SystemExit(1)
print("UI layout restoration contracts passed")
