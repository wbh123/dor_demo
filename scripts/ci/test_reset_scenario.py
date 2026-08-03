#!/usr/bin/env python3
from __future__ import annotations

import unittest

from reset_scenario import DEFAULT_SCENARIO, VALID_SCENARIOS, resolve_scenario


class ResetScenarioTest(unittest.TestCase):
    def test_default_keeps_backward_compatible_realistic_scenario(self) -> None:
        self.assertEqual("realistic", DEFAULT_SCENARIO)

    def test_only_clean_and_realistic_are_supported(self) -> None:
        self.assertEqual(("clean", "realistic"), VALID_SCENARIOS)

    def test_clean_scenario_selects_clean_source_and_marker(self) -> None:
        scenario = resolve_scenario(" clean ")
        self.assertEqual("clean", scenario.name)
        self.assertEqual("1000_students_clean.sql", scenario.source_file)
        self.assertEqual("CLEAN_1000_READY", scenario.ready_marker)
        self.assertFalse(scenario.includes_operational_state)

    def test_realistic_scenario_selects_operational_source_and_marker(self) -> None:
        scenario = resolve_scenario("REALISTIC")
        self.assertEqual("realistic", scenario.name)
        self.assertEqual("1000_students_realistic_mixed_state.sql", scenario.source_file)
        self.assertEqual("REALISTIC_1000_READY", scenario.ready_marker)
        self.assertTrue(scenario.includes_operational_state)

    def test_unknown_scenario_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "clean.*realistic"):
            resolve_scenario("random")


if __name__ == "__main__":
    unittest.main(verbosity=2)
