#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/VerifiedTeamInvitationService.java"
MAPPER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/mapper/VerifiedTeamInvitationMapper.java"
XML = ROOT / "backend-java/server/src/main/resources/mapper/student/VerifiedTeamInvitationMapper.xml"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


service = SERVICE.read_text(encoding="utf-8")
require("NamedParameterJdbcTemplate" not in service, "VerifiedTeamInvitationService must not depend on JDBC directly")
require("jdbc." not in service, "VerifiedTeamInvitationService must not execute JDBC operations")
require("VerifiedTeamInvitationMapper" in service, "VerifiedTeamInvitationService must delegate persistence to a dedicated mapper")
require(MAPPER.exists(), "VerifiedTeamInvitationMapper.java is required")
require(XML.exists(), "VerifiedTeamInvitationMapper.xml is required")

mapper = MAPPER.read_text(encoding="utf-8")
xml = XML.read_text(encoding="utf-8")
for method in (
    "findEligibleInvitee",
    "findLeaderTeamForUpdate",
    "findInvitationGuards",
    "upsertInvitedMember",
    "insertInvitation",
    "hasPendingInvitation",
    "findPendingInvitationForUpdate",
    "cancelInvitation",
    "removeInvitedMember",
    "insertCancellationNotification",
):
    require(method in mapper, f"mapper method missing: {method}")
    require(f'id="{method}"' in xml, f"mapper XML statement missing: {method}")

require("FOR UPDATE" in xml, "team/invitation mutation context must preserve FOR UPDATE locking")
require("SELECT *" not in xml.upper(), "team invitation mapper must not use SELECT *")
require("findInvitationGuards" in service, "invite flow must use one consolidated guard query")
require(service.count("findInvitationGuards(") == 1, "invite flow must execute the consolidated guard query exactly once")

# Guard query must be set based: one statement aggregates member occupancy, pending invites,
# invitee membership and duplicate pending invitation. It must not use correlated per-row lookups.
guard_start = xml.index('id="findInvitationGuards"')
guard_end = xml.find("</select>", guard_start)
guard_sql = xml[guard_start:guard_end].upper()
require("JOINED_ELSEWHERE" in guard_sql, "guard query must return invitee membership state")
require("DUPLICATE_PENDING" in guard_sql, "guard query must return duplicate invitation state")
require("OCCUPIED_COUNT" in guard_sql, "guard query must return occupied team slots")
require("SUM(CASE" in guard_sql or "COUNT(" in guard_sql, "guard query must aggregate team/invitation state")

print("verified team invitation MyBatis contract: OK")
