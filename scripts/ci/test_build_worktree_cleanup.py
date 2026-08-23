#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CLEANUP = ROOT / "scripts/ci/cleanup_build_worktree.sh"


def run(*command: str, cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(list(command), cwd=cwd, text=True, capture_output=True, check=False)


def init_repo(root: Path) -> None:
    run("git", "init", "-q", cwd=root)
    run("git", "config", "user.email", "ci@example.invalid", cwd=root)
    run("git", "config", "user.name", "CI", cwd=root)
    (root / ".gitignore").write_text("frontend/dist/\napp/android/app/build/\n", encoding="utf-8")
    files = {
        "README.md": "base\n",
        "frontend/src/api/schema.d.ts": "frontend base\n",
        "frontend/src/api/systemReadinessSchema.d.ts": "readiness base\n",
        "frontend/public/fonts/font.ttf": "font base\n",
        "app/src/api/schema.d.ts": "app base\n",
        "app/src/generated/school-profile.ts": "profile base\n",
        "app/capacitor.config.ts": "capacitor base\n",
        "app/src/assets/icon.png": "icon base\n",
        "app/android/capacitor.settings.gradle": "android base\n",
    }
    for relative, content in files.items():
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
    run("git", "add", ".", cwd=root)
    run("git", "commit", "-qm", "base", cwd=root)


class CleanupTest(unittest.TestCase):
    def test_frontend_generated_change_is_restored_and_ignored_output_survives(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            init_repo(root)
            (root / "frontend/src/api/schema.d.ts").write_text("generated\n", encoding="utf-8")
            dist = root / "frontend/dist/index.html"
            dist.parent.mkdir(parents=True, exist_ok=True)
            dist.write_text("built\n", encoding="utf-8")
            result = run("bash", str(CLEANUP), str(root), "frontend", cwd=root)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual((root / "frontend/src/api/schema.d.ts").read_text(), "frontend base\n")
            self.assertEqual(dist.read_text(), "built\n")

    def test_app_generated_change_is_restored(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            init_repo(root)
            (root / "app/android/capacitor.settings.gradle").write_text("generated\n", encoding="utf-8")
            result = run("bash", str(CLEANUP), str(root), "app", cwd=root)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(run("git", "status", "--porcelain", "--untracked-files=no", cwd=root).stdout, "")

    def test_unexpected_tracked_change_is_not_overwritten(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            init_repo(root)
            (root / "frontend/src/api/schema.d.ts").write_text("generated\n", encoding="utf-8")
            (root / "README.md").write_text("human edit\n", encoding="utf-8")
            result = run("bash", str(CLEANUP), str(root), "frontend", cwd=root)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("README.md", result.stderr)
            self.assertEqual((root / "README.md").read_text(), "human edit\n")
            self.assertEqual((root / "frontend/src/api/schema.d.ts").read_text(), "generated\n")

    def test_no_git_clean(self) -> None:
        source = CLEANUP.read_text(encoding="utf-8")
        self.assertNotIn("git clean", source)
        self.assertIn("git restore", source)


if __name__ == "__main__":
    unittest.main()
