#!/usr/bin/env python3
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LEGACY_VALIDATOR = ROOT / "scripts/ci/validate_system_contracts.py"

old_block = '''    require(
        "TEAM_ASSIGNED_FORBIDDEN" in team_service
        and "请先创建处于组队中的队伍" in team_service
        and "请先创建处于组队中的队伍" in team_view,
        "assigned-student team restrictions or invitation guidance are missing",
        errors,
    )'''
new_block = '''    require(
        "TEAM_ASSIGNED_FORBIDDEN" in team_service
        and "ensureFormingLeaderTeam" in team_service
        and "/api/v1/student/team-invitations" in team_view
        and "createTeam()" not in team_view,
        "assigned-student restriction or invitation-driven automatic team creation is missing",
        errors,
    )'''

source = LEGACY_VALIDATOR.read_text(encoding="utf-8")
if old_block not in source:
    raise SystemExit("unable to update the legacy manual-team contract")
source = source.replace(old_block, new_block, 1)
namespace = {
    "__name__": "validate_system_contracts_v25_runtime",
    "__file__": str(LEGACY_VALIDATOR),
}
exec(compile(source, str(LEGACY_VALIDATOR), "exec"), namespace)
raise SystemExit(namespace["main"]())
