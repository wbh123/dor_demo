#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BOOTSTRAP = ROOT / "scripts/ci/bootstrap_host_contract.sh"


class HostBootstrapTest(unittest.TestCase):
    def run_dry(self, distro: str, codename: str) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            os_release = root / "os-release"
            os_release.write_text(
                f"ID={distro}\nVERSION_CODENAME={codename}\nUBUNTU_CODENAME={codename}\n",
                encoding="utf-8",
            )
            env = os.environ.copy()
            env["WUST_DORMITORY_BOOTSTRAP_DRY_RUN"] = "1"
            env["WUST_DORMITORY_OS_RELEASE_FILE"] = str(os_release)
            env["WUST_DORMITORY_BOOTSTRAP_USER"] = "contest"
            return subprocess.run(
                ["bash", str(BOOTSTRAP)],
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )

    def test_ubuntu_prefers_aliyun_docker_ce_and_installs_compose_plugin(self) -> None:
        result = self.run_dry("ubuntu", "noble")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        output = result.stdout
        self.assertIn("https://mirrors.aliyun.com/docker-ce/linux/ubuntu/gpg", output)
        self.assertIn("https://mirrors.aliyun.com/docker-ce/linux/ubuntu", output)
        for package in (
            "docker-ce",
            "docker-ce-cli",
            "containerd.io",
            "docker-buildx-plugin",
            "docker-compose-plugin",
        ):
            self.assertIn(package, output)
        self.assertIn("usermod -aG docker contest", output)

    def test_debian_uses_debian_repository(self) -> None:
        result = self.run_dry("debian", "bookworm")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("https://mirrors.aliyun.com/docker-ce/linux/debian", result.stdout)

    def test_official_docker_repository_is_retained_as_fallback(self) -> None:
        source = BOOTSTRAP.read_text(encoding="utf-8")
        self.assertIn("https://download.docker.com/linux/${distro}/gpg", source)
        self.assertIn("https://download.docker.com/linux/${distro}", source)
        self.assertIn("Docker CE 国内镜像安装失败", source)

    def test_bootstrap_is_non_destructive(self) -> None:
        source = BOOTSTRAP.read_text(encoding="utf-8")
        self.assertNotIn("rm -rf /var/lib/docker", source)
        self.assertNotIn("rm -rf /var/lib/containerd", source)
        self.assertNotIn("apt-get remove", source)
        self.assertNotIn("apt remove", source)

    def test_unsupported_distribution_fails_explicitly(self) -> None:
        result = self.run_dry("alpine", "edge")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("仅支持 Ubuntu/Debian", result.stderr)

    def test_shell_syntax(self) -> None:
        result = subprocess.run(["bash", "-n", str(BOOTSTRAP)], text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
