#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    source = ROOT / path
    if not source.exists():
        raise AssertionError(f"missing required file: {path}")
    content = source.read_text(encoding="utf-8")
    if source.suffix == ".vue":
        for suffix in (".logic.ts", ".template.html", ".css"):
            companion = source.with_name(f"{source.stem}{suffix}")
            if companion.exists():
                content += "\n" + companion.read_text(encoding="utf-8")
    return content


batch = read("frontend/src/views/admin/AdminBatchView.vue")
density = read("frontend/src/admin-density-refinement.css")
confirm = read("frontend/src/components/modal/AppConfirmDialog.vue")
style = read("frontend/src/style.css")
shell = read("frontend/src/layouts/AppShell.vue")
team_guard = read("backend-java/server/src/main/java/com/wust/dormitory/student/TeamAssignmentGuardAspect.java")
team_service = read("backend-java/server/src/main/java/com/wust/dormitory/student/TeamService.java")
auth_service = read("backend-java/server/src/main/java/com/wust/dormitory/auth/AuthService.java")
auth_contract = read("backend-java/model/src/main/resources/auth/openapi-auth.yaml")
password_view = read("frontend/src/views/admin/AdminPasswordView.vue")
bootstrap_admin = read("backend-java/docs/sql/bootstrap_test_admin.sql")
selection_policy = read("backend-java/server/src/main/java/com/wust/dormitory/selection/SelectionPolicyService.java")

assert "scope-student-filter-grid" in batch
assert "scope-room-filter-grid" in batch
assert "scope-filter-compact" in batch
assert "grid-template-columns:repeat(4" in density.replace(" ", "")
assert "min-height:36px" in density.replace(" ", "")
assert ':show-symbol="false"' in batch
assert "showSymbol?: boolean" in confirm
assert 'v-if="showSymbol"' in confirm
compact_style = style.replace(" ", "").replace("\n", "")
assert ".page-container{width:100%;max-width:none;margin:0;" in compact_style
assert ".content-column.narrow{width:100%;max-width:none;margin:0;" in compact_style
assert "<template #header>" in shell
assert "welcome-dialog" in shell
assert "welcome-glow-one" in shell
assert ':title="t(\'welcome.title\')"' not in shell
assert "ensureFormingLeaderTeam" in team_service
assert "TEAM_FORMING_REQUIRED" not in team_guard
assert "请先创建处于组队中的队伍" not in team_guard
assert "formationService.requireUnassigned" in team_guard
assert "newPassword.length() < 4" in auth_service
assert "newPassword.value.length >= 4" in password_view
assert "newPassword: { type: string, minLength: 4" in auth_contract
assert "{noop}0000" in bootstrap_admin
assert "upsertPolicySetting" in selection_policy
assert "updated_by=:updatedBy" in selection_policy
assert "private boolean settingEnabled" in selection_policy
assert "private void ensure()" not in selection_policy
print("Compact scope, policy, team, password and welcome regression contracts passed")
