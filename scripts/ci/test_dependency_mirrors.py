#!/usr/bin/env python3
from __future__ import annotations

import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
INSTALLER = ROOT / "scripts/ci/install_dependencies_with_mirrors.sh"


class DependencyMirrorTest(unittest.TestCase):
    def make_fake_commands(self, root: Path, *, apt_mirror_fails: bool, pip_mirror_fails: bool) -> tuple[dict[str, str], Path]:
        bin_dir = root / "bin"
        bin_dir.mkdir()
        log = root / "commands.log"
        for name in ("apt-get", "python3"):
            script = bin_dir / name
            script.write_text(
                """#!/usr/bin/env bash
set -euo pipefail
printf '%s %s\\n' \"$(basename \"$0\")\" \"$*\" >> \"${COMMAND_LOG}\"
if [[ \"$(basename \"$0\")\" == \"apt-get\" && \"${APT_MIRROR_FAILS:-0}\" == \"1\" && -f \"${APT_MIRROR_ACTIVE}\" ]]; then exit 42; fi
if [[ \"$(basename \"$0\")\" == \"python3\" && \"$*\" == *\"mirrors.aliyun.com/pypi/simple\"* && \"${PIP_MIRROR_FAILS:-0}\" == \"1\" ]]; then exit 43; fi
exit 0
""",
                encoding="utf-8",
            )
            script.chmod(script.stat().st_mode | stat.S_IXUSR)
        env = os.environ.copy()
        env["PATH"] = f"{bin_dir}:{env['PATH']}"
        env["COMMAND_LOG"] = str(log)
        env["APT_MIRROR_FAILS"] = "1" if apt_mirror_fails else "0"
        env["PIP_MIRROR_FAILS"] = "1" if pip_mirror_fails else "0"
        env["WUST_APT_ROOT"] = str(root / "apt")
        env["APT_MIRROR_ACTIVE"] = str(root / "apt/mirror-active")
        return env, log

    def prepare_apt_root(self, root: Path) -> None:
        apt = root / "apt"
        (apt / "sources.list.d").mkdir(parents=True)
        (apt / "sources.list").write_text(
            "deb http://archive.ubuntu.com/ubuntu jammy main\n"
            "deb http://security.ubuntu.com/ubuntu jammy-security main\n",
            encoding="utf-8",
        )

    def run_installer(self, *, apt_mirror_fails: bool, pip_mirror_fails: bool) -> tuple[subprocess.CompletedProcess[str], list[str], str]:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            self.prepare_apt_root(root)
            requirements = root / "requirements.txt"
            requirements.write_text("minio==7.2.20\n", encoding="utf-8")
            env, log = self.make_fake_commands(root, apt_mirror_fails=apt_mirror_fails, pip_mirror_fails=pip_mirror_fails)
            result = subprocess.run(
                ["bash", str(INSTALLER), "--requirements", str(requirements), "curl", "git"],
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )
            calls = log.read_text(encoding="utf-8").splitlines() if log.exists() else []
            sources = (root / "apt/sources.list").read_text(encoding="utf-8")
            return result, calls, sources

    def test_aliyun_apt_and_pip_are_preferred(self) -> None:
        result, calls, sources = self.run_installer(apt_mirror_fails=False, pip_mirror_fails=False)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("https://mirrors.aliyun.com/ubuntu", sources)
        self.assertTrue(any("python3 -m pip install" in call and "https://mirrors.aliyun.com/pypi/simple/" in call for call in calls))

    def test_official_sources_are_restored_when_aliyun_fails(self) -> None:
        result, calls, sources = self.run_installer(apt_mirror_fails=True, pip_mirror_fails=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("archive.ubuntu.com/ubuntu", sources)
        apt_updates = [call for call in calls if call == "apt-get update"]
        self.assertGreaterEqual(len(apt_updates), 2)
        pip_calls = [call for call in calls if call.startswith("python3 -m pip install")]
        self.assertGreaterEqual(len(pip_calls), 2)
        self.assertIn("mirrors.aliyun.com/pypi/simple", pip_calls[0])
        self.assertNotIn("mirrors.aliyun.com/pypi/simple", pip_calls[-1])

    def test_shell_syntax(self) -> None:
        result = subprocess.run(["bash", "-n", str(INSTALLER)], text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
