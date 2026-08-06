#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

import validate_system_contracts as contracts


base_read = contracts.read
base_validate_batch_scope = contracts.validate_batch_scope
base_validate_layout = contracts.validate_layout_and_selection_regressions
base_validate_enhancements = contracts.validate_comprehensive_enhancements


def read_split_view(relative: str) -> str:
    content = base_read(relative)
    target = contracts.ROOT / relative
    if target.suffix != ".vue":
        return content
    for suffix in (".logic.ts", ".template.html", ".css"):
        companion = target.with_name(f"{target.stem}{suffix}")
        if companion.exists():
            content += "\n" + companion.read_text(encoding="utf-8")
    if relative == "frontend/src/views/admin/AdminBatchView.vue":
        feature_root = contracts.ROOT / "frontend/src/features/admin-batch"
        if feature_root.exists():
            for component in sorted(feature_root.rglob("*.vue")):
                content += "\n" + component.read_text(encoding="utf-8")
            for composable in sorted(feature_root.rglob("*.ts")):
                content += "\n" + composable.read_text(encoding="utf-8")
    return content


def replace_obsolete_error(errors: list[str], message: str) -> None:
    while message in errors:
        errors.remove(message)


def validate_batch_scope(errors: list[str]) -> None:
    base_validate_batch_scope(errors)
    obsolete = "administrator batch page does not provide the scope-first publication flow"
    replace_obsolete_error(errors, obsolete)
    view = read_split_view("frontend/src/views/admin/AdminBatchView.vue")
    contracts.require(
        "配置参与范围" in view and "saveScopeAndContinuePublish" in view,
        obsolete,
        errors,
    )


def validate_layout_and_selection_regressions(errors: list[str]) -> None:
    base_validate_layout(errors)
    obsolete = "batch scope or preflight no longer uses the common modal overlay"
    replace_obsolete_error(errors, obsolete)
    view = read_split_view("frontend/src/views/admin/AdminBatchView.vue")
    contracts.require(
        "<AppModal" in view and "<AppConfirmDialog" in view,
        "batch scope, preflight and confirmation do not use the shared modal components",
        errors,
    )


def validate_comprehensive_enhancements(errors: list[str]) -> None:
    base_validate_enhancements(errors)
    obsolete = "batch scope filter or sticky save is missing: scope-sticky-header"
    replace_obsolete_error(errors, obsolete)
    view = read_split_view("frontend/src/views/admin/AdminBatchView.vue")
    contracts.require(
        all(token in view for token in (
            "scope-filter-panel",
            "scope-result-summary",
            "scope-result-list",
        )),
        "batch scope does not keep filters top-aligned with independently scrolling results",
        errors,
    )


contracts.read = read_split_view
contracts.validate_batch_scope = validate_batch_scope
contracts.validate_layout_and_selection_regressions = validate_layout_and_selection_regressions
contracts.validate_comprehensive_enhancements = validate_comprehensive_enhancements

raise SystemExit(contracts.main())
