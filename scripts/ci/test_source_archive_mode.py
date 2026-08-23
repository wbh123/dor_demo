#!/usr/bin/env python3
from __future__ import annotations

import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "scripts/ci/source_archive_mode_contract.sh"


class SourceArchiveModeContractTest(unittest.TestCase):
    def test_archive_mode_does_not_invoke_git(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            fake_git = bin_dir / "git"
            fake_git.write_text("#!/usr/bin/env bash\nexit 97\n", encoding="utf-8")
            fake_git.chmod(fake_git.stat().st_mode | stat.S_IXUSR)
            env = os.environ.copy()
            env["PATH"] = f"{bin_dir}:{env['PATH']}"
            result = subprocess.run(
                ["bash", str(CONTRACT), str(root)],
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("MODE=ARCHIVE", result.stdout)
            self.assertIn("FULL=1", result.stdout)
            self.assertIn("PULL=0", result.stdout)
            self.assertIn("HEAD=ARCHIVE", result.stdout)

    def test_git_mode_keeps_incremental_defaults(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            subprocess.run(["git", "-C", str(root), "config", "user.email", "ci@example.invalid"], check=True)
            subprocess.run(["git", "-C", str(root), "config", "user.name", "CI"], check=True)
            (root / "README.md").write_text("x\n", encoding="utf-8")
            subprocess.run(["git", "-C", str(root), "add", "README.md"], check=True)
            subprocess.run(["git", "-C", str(root), "commit", "-qm", "init"], check=True)
            result = subprocess.run(
                ["bash", str(CONTRACT), str(root)],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("MODE=GIT", result.stdout)
            self.assertIn("FULL=0", result.stdout)
            self.assertIn("PULL=1", result.stdout)
            self.assertRegex(result.stdout, r"HEAD=[0-9a-f]{40}")

    def test_release_tag_falls_back_without_git_worktree(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            result = subprocess.run(
                ["bash", str(CONTRACT), "--release-tag", str(root)],
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertRegex(result.stdout.strip(), r"^archive-[0-9]{8}T[0-9]{6}Z$")

    def test_shell_syntax(self) -> None:
        result = subprocess.run(["bash", "-n", str(CONTRACT)], text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
