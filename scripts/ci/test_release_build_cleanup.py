#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "scripts/ci/release_build_cleanup_contract.sh"


def run(*command: str, cwd: Path | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(list(command), cwd=cwd, text=True, capture_output=True, check=False)


def init_repo(root: Path) -> None:
    run("git", "init", "-q", cwd=root)
    run("git", "config", "user.email", "ci@example.invalid", cwd=root)
    run("git", "config", "user.name", "CI", cwd=root)
    generated = root / "frontend/src/api/schema.d.ts"
    generated.parent.mkdir(parents=True)
    generated.write_text("baseline\n", encoding="utf-8")
    (root / "README.md").write_text("baseline\n", encoding="utf-8")
    run("git", "add", ".", cwd=root)
    committed = run("git", "commit", "-qm", "base", cwd=root)
    if committed.returncode != 0:
        raise RuntimeError(committed.stderr)


class ReleaseBuildCleanupTest(unittest.TestCase):
    def test_successful_release_build_restores_generated_tracked_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            init_repo(repo)
            result = run("bash", str(CONTRACT), str(repo), "success")
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertEqual((repo / "frontend/src/api/schema.d.ts").read_text(encoding="utf-8"), "baseline\n")
            status = run("git", "status", "--porcelain", "--untracked-files=no", cwd=repo)
            self.assertEqual(status.stdout, "")

    def test_failed_release_build_still_restores_generated_tracked_file(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            init_repo(repo)
            result = run("bash", str(CONTRACT), str(repo), "fail")
            self.assertEqual(result.returncode, 42, result.stdout + result.stderr)
            self.assertEqual((repo / "frontend/src/api/schema.d.ts").read_text(encoding="utf-8"), "baseline\n")
            status = run("git", "status", "--porcelain", "--untracked-files=no", cwd=repo)
            self.assertEqual(status.stdout, "")

    def test_preexisting_tracked_change_is_rejected_and_preserved(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            repo = Path(temporary)
            init_repo(repo)
            (repo / "README.md").write_text("human edit\n", encoding="utf-8")
            result = run("bash", str(CONTRACT), str(repo), "success")
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("dirty before release build", result.stderr)
            self.assertEqual((repo / "README.md").read_text(encoding="utf-8"), "human edit\n")
            self.assertEqual((repo / "frontend/src/api/schema.d.ts").read_text(encoding="utf-8"), "baseline\n")

    def test_shell_syntax(self) -> None:
        result = run("bash", "-n", str(CONTRACT))
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
