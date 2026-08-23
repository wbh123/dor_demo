#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PLAN = ROOT / "scripts/ci/incremental_build_plan.sh"


def run(*command: str, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(list(command), cwd=cwd, text=True, capture_output=True, check=False)


def init_repo(root: Path) -> str:
    run("git", "init", "-q", cwd=root)
    run("git", "config", "user.email", "ci@example.invalid", cwd=root)
    run("git", "config", "user.name", "CI", cwd=root)
    (root / "README.md").write_text("base\n", encoding="utf-8")
    run("git", "add", ".", cwd=root)
    run("git", "commit", "-qm", "base", cwd=root)
    return run("git", "rev-parse", "HEAD", cwd=root).stdout.strip()


def add_commit(root: Path, relative: str) -> str:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("changed\n", encoding="utf-8")
    run("git", "add", relative, cwd=root)
    run("git", "commit", "-qm", f"change {relative}", cwd=root)
    return run("git", "rev-parse", "HEAD", cwd=root).stdout.strip()


def parse(output: str) -> dict[str, int]:
    result: dict[str, int] = {}
    for line in output.splitlines():
        if "=" not in line:
            continue
        key, value = line.split("=", 1)
        if value in {"0", "1"}:
            result[key] = int(value)
    return result


class IncrementalBuildPlanTest(unittest.TestCase):
    def plan_for(self, path: str) -> dict[str, int]:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            base = init_repo(repo)
            head = add_commit(repo, path)
            completed = run("bash", str(PLAN), base, head, cwd=repo)
            self.assertEqual(completed.returncode, 0, completed.stderr)
            return parse(completed.stdout)

    def test_frontend_change_builds_only_frontend_without_nginx_restart(self) -> None:
        plan = self.plan_for("frontend/src/App.vue")
        self.assertEqual(plan["FRONTEND_BUILD"], 1)
        self.assertEqual(plan["NGINX_RESTART"], 0)
        self.assertEqual(plan["BACKEND_COMPILE"], 0)
        self.assertEqual(plan["BACKEND_RUNTIME"], 0)
        self.assertEqual(plan["APP_BUILD"], 0)

    def test_backend_java_change_builds_and_restarts_only_backend(self) -> None:
        plan = self.plan_for("backend-java/server/src/main/java/example/Test.java")
        self.assertEqual(plan["BACKEND_COMPILE"], 1)
        self.assertEqual(plan["BACKEND_RUNTIME"], 1)
        self.assertEqual(plan["FRONTEND_BUILD"], 0)
        self.assertEqual(plan["APP_BUILD"], 0)
        self.assertEqual(plan["NGINX_RESTART"], 0)

    def test_mobile_contract_change_builds_backend_frontend_and_app(self) -> None:
        plan = self.plan_for("backend-java/model/src/main/resources/mobile/openapi-mobile-interface.yaml")
        self.assertEqual(plan["BACKEND_COMPILE"], 1)
        self.assertEqual(plan["BACKEND_RUNTIME"], 1)
        self.assertEqual(plan["FRONTEND_BUILD"], 1)
        self.assertEqual(plan["APP_BUILD"], 1)
        self.assertEqual(plan["NGINX_RESTART"], 0)

    def test_backup_runtime_resource_rebuilds_backend_image_without_maven_compile(self) -> None:
        plan = self.plan_for("backend-java/docs/sql/navicat/check.sql")
        self.assertEqual(plan["BACKEND_COMPILE"], 0)
        self.assertEqual(plan["BACKEND_RUNTIME"], 1)

    def test_same_commit_is_noop(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            commit = init_repo(repo)
            completed = run("bash", str(PLAN), commit, commit, cwd=repo)
            self.assertEqual(completed.returncode, 0, completed.stderr)
            self.assertTrue(all(value == 0 for value in parse(completed.stdout).values()))


if __name__ == "__main__":
    unittest.main()
