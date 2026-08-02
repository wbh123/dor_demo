#!/usr/bin/env python3
from __future__ import annotations

import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BED_HOLD = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/selection/BedHoldService.java"
TEAM_HOLD_RELEASE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/TeamHoldReleaseService.java"
STUDENT_CONTROLLER = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java"
I18N = ROOT / "frontend/src/i18n/index.ts"
WELCOME_MIGRATION = ROOT / "backend-java/server/src/main/resources/db/migration/V11__harden_welcome_message_json.sql"
WELCOME_SERVICE = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/auth/StudentWelcomeService.java"
ROOM_LAYOUT = ROOT / "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"


class PostMergeHardeningTest(unittest.TestCase):
    def test_team_membership_changes_and_holds_share_one_database_lock(self) -> None:
        hold = BED_HOLD.read_text(encoding="utf-8")
        guard = TEAM_HOLD_RELEASE.read_text(encoding="utf-8")
        controller = STUDENT_CONTROLLER.read_text(encoding="utf-8")
        self.assertIn("public boolean isHeldByTeam", hold)
        self.assertIn('value.startsWith("T:" + teamId + ":")', hold)
        self.assertIn("requireNoActiveHoldForLeader", guard)
        self.assertIn("requireNoActiveHoldForMember", guard)
        self.assertIn("requireNoActiveHoldForPersonalSelection", guard)
        self.assertIn("lockTeamForHold", guard)
        self.assertGreaterEqual(guard.count("FOR UPDATE"), 4)
        self.assertIn("assertNoActiveTeamHold", guard)
        self.assertIn("TEAM_HOLD_ACTIVE", guard)
        self.assertIn("请先释放队伍临时占用的床位", guard)
        self.assertIn("batch_bed_scope", guard)
        self.assertIn("batch_room_scope", guard)
        self.assertIn("batch_building_scope", guard)
        self.assertIn("JOIN dormitory_floor", guard)
        self.assertIn("teamHoldReleaseService.requireNoActiveHoldForLeader", controller)
        self.assertIn("teamHoldReleaseService.requireNoActiveHoldForMember", controller)
        self.assertIn("teamHoldReleaseService.requireNoActiveHoldForPersonalSelection", controller)
        self.assertIn("teamHoldReleaseService.lockTeamForHold", controller)
        self.assertGreaterEqual(controller.count("@Transactional"), 4)

    def test_vue_i18n_refreshes_source_for_application_text_changes(self) -> None:
        i18n = I18N.read_text(encoding="utf-8")
        self.assertIn("MutationObserver", i18n)
        self.assertIn("lastAppliedText", i18n)
        self.assertIn("originalText.set(mutation.target, current)", i18n)
        self.assertIn("lastAppliedText.get(mutation.target) === current", i18n)
        self.assertNotIn("if (applying) return", i18n)

    def test_welcome_migration_and_reader_accept_only_locale_objects(self) -> None:
        migration = WELCOME_MIGRATION.read_text(encoding="utf-8")
        welcome = WELCOME_SERVICE.read_text(encoding="utf-8")
        self.assertIn("JSON_TYPE(setting_value) <> 'OBJECT'", migration)
        self.assertIn("JSON_EXTRACT(setting_value, '$.zh-CN')", migration)
        self.assertIn("configured == null", welcome)
        self.assertIn("configured.forEach", welcome)

    def test_layout_only_collapses_genuine_bunk_pairs(self) -> None:
        service = ROOM_LAYOUT.read_text(encoding="utf-8")
        self.assertIn("isGenuineBunkPair", service)
        self.assertIn('"BUNK_UPPER"', service)
        self.assertIn('"BUNK_LOWER"', service)
        self.assertIn("LOFT_BED_DESK", service)


if __name__ == "__main__":
    unittest.main()
