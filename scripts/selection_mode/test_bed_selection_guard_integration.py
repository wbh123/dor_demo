from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]


def read(path: str) -> str:
    target = ROOT / path
    return target.read_text(encoding="utf-8") if target.exists() else ""


class BedSelectionGuardIntegrationTest(unittest.TestCase):
    def test_personal_and_team_bed_operations_use_the_guard(self):
        controller = read(
            "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java"
        )
        self.assertIn("BedSelectionEligibilityGuard", controller)
        self.assertGreaterEqual(
            controller.count("bedSelectionEligibilityGuard.requirePersonalAllowed"),
            2,
        )
        self.assertGreaterEqual(
            controller.count("bedSelectionEligibilityGuard.requireTeamAllowed"),
            2,
        )

    def test_guard_checks_scope_category_residency_and_mapping(self):
        guard = read(
            "backend-java/server/src/main/java/com/wust/dormitory/residency/BedSelectionEligibilityGuard.java"
        )
        for token in (
            "requireBedInBatch",
            "requireRoomLockedByBatch",
            "requireStudentEligibleForRoom",
            "requireNoActiveResidency",
            "unknownBedResidentCount",
            "TEAM_STUDENT_CATEGORY_MISMATCH",
            "TEAM_BEDS_NOT_IN_SAME_ROOM",
        ):
            self.assertIn(token, guard)

    def test_release_operations_remain_available_for_cleanup(self):
        controller = read(
            "backend-java/server/src/main/java/com/wust/dormitory/student/StudentController.java"
        )
        release_section = controller[
            controller.index("releaseBed(") : controller.index("confirmBed(")
        ]
        team_release_section = controller[
            controller.index("releaseTeamBeds(") : controller.index("confirmTeamBeds(")
        ]
        self.assertNotIn("requirePersonalAllowed", release_section)
        self.assertNotIn("requireTeamAllowed", team_release_section)
        self.assertIn("bedScopeGuard.requireAllowed", release_section)
        self.assertIn("bedScopeGuard.requireAllowed", team_release_section)


if __name__ == "__main__":
    unittest.main()
