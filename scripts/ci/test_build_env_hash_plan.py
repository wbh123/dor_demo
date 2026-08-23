#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "scripts/ci/build_env_hash_plan_contract.sh"


def parse(output: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in output.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            result[key] = value
    return result


class BuildEnvHashPlanTest(unittest.TestCase):
    def run_plan(
        self,
        env_file: Path,
        frontend_hash: str = "",
        app_hash: str = "",
        skip_app: bool = False,
    ) -> dict[str, str]:
        result = subprocess.run(
            [
                "bash",
                str(CONTRACT),
                str(env_file),
                frontend_hash,
                app_hash,
                "1" if skip_app else "0",
            ],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return parse(result.stdout)

    def test_non_vite_runtime_env_change_does_not_trigger_frontend_or_app_build(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text("VITE_APP_TITLE=A\nWUST_SECRET=one\n", encoding="utf-8")
            initial = self.run_plan(env_file)
            current_hash = initial["CURRENT_BUILD_ENV_SHA256"]
            env_file.write_text("VITE_APP_TITLE=A\nWUST_SECRET=two\n", encoding="utf-8")
            plan = self.run_plan(env_file, current_hash, current_hash)
            self.assertEqual(plan["FRONTEND_BUILD"], "0")
            self.assertEqual(plan["APP_BUILD"], "0")

    def test_vite_env_change_rebuilds_frontend_and_app(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text("VITE_APP_TITLE=A\nWUST_SECRET=one\n", encoding="utf-8")
            old = self.run_plan(env_file)["CURRENT_BUILD_ENV_SHA256"]
            env_file.write_text("VITE_APP_TITLE=B\nWUST_SECRET=one\n", encoding="utf-8")
            plan = self.run_plan(env_file, old, old)
            self.assertEqual(plan["FRONTEND_BUILD"], "1")
            self.assertEqual(plan["APP_BUILD"], "1")
            self.assertNotEqual(plan["CURRENT_BUILD_ENV_SHA256"], old)

    def test_skip_app_does_not_advance_app_build_env_state(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text("VITE_APP_TITLE=A\n", encoding="utf-8")
            old = self.run_plan(env_file)["CURRENT_BUILD_ENV_SHA256"]
            env_file.write_text("VITE_APP_TITLE=B\n", encoding="utf-8")
            skipped = self.run_plan(env_file, old, old, skip_app=True)
            current = skipped["CURRENT_BUILD_ENV_SHA256"]
            self.assertEqual(skipped["FRONTEND_BUILD"], "1")
            self.assertEqual(skipped["APP_BUILD"], "0")
            self.assertEqual(skipped["FRONTEND_STATE_AFTER_SUCCESS"], current)
            self.assertEqual(skipped["APP_STATE_AFTER_SUCCESS"], old)

            next_run = self.run_plan(env_file, current, old, skip_app=False)
            self.assertEqual(next_run["FRONTEND_BUILD"], "0")
            self.assertEqual(next_run["APP_BUILD"], "1")

    def test_missing_state_hash_causes_one_time_rebuild_for_migration(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text("VITE_APP_TITLE=A\n", encoding="utf-8")
            plan = self.run_plan(env_file)
            self.assertEqual(plan["FRONTEND_BUILD"], "1")
            self.assertEqual(plan["APP_BUILD"], "1")

    def test_shell_syntax(self) -> None:
        result = subprocess.run(["bash", "-n", str(CONTRACT)], text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
