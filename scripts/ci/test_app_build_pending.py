#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "scripts/ci/app_build_pending_contract.sh"


def plan(requested: int, pending: int, skip: int) -> dict[str, str]:
    result = subprocess.run(
        ["bash", str(CONTRACT), str(requested), str(pending), str(skip)],
        text=True,
        capture_output=True,
        check=False,
    )
    if result.returncode != 0:
        raise AssertionError(result.stdout + result.stderr)
    values: dict[str, str] = {}
    for line in result.stdout.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            values[key] = value
    return values


class AppBuildPendingTest(unittest.TestCase):
    def test_skip_app_records_pending_when_app_build_was_required(self) -> None:
        result = plan(requested=1, pending=0, skip=1)
        self.assertEqual(result["APP_BUILD"], "0")
        self.assertEqual(result["APP_BUILD_PENDING_AFTER_SUCCESS"], "1")

    def test_next_normal_deploy_replays_pending_app_build(self) -> None:
        result = plan(requested=0, pending=1, skip=0)
        self.assertEqual(result["APP_BUILD"], "1")
        self.assertEqual(result["APP_BUILD_PENDING_AFTER_SUCCESS"], "0")

    def test_skip_without_app_change_does_not_create_pending_work(self) -> None:
        result = plan(requested=0, pending=0, skip=1)
        self.assertEqual(result["APP_BUILD"], "0")
        self.assertEqual(result["APP_BUILD_PENDING_AFTER_SUCCESS"], "0")

    def test_repeated_skip_preserves_existing_pending_work(self) -> None:
        result = plan(requested=0, pending=1, skip=1)
        self.assertEqual(result["APP_BUILD"], "0")
        self.assertEqual(result["APP_BUILD_PENDING_AFTER_SUCCESS"], "1")

    def test_shell_syntax(self) -> None:
        result = subprocess.run(["bash", "-n", str(CONTRACT)], text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
