#!/usr/bin/env python3
from __future__ import annotations

import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PULL = ROOT / "scripts/ci/pull_image_with_fallback.sh"

IMAGES = (
    "m.daocloud.io/docker.io/library/node:22-bookworm",
    "m.daocloud.io/docker.io/library/maven:3.9.11-eclipse-temurin-21",
    "m.daocloud.io/docker.io/library/eclipse-temurin:21-jre-jammy",
    "m.daocloud.io/docker.io/library/mysql:8.4",
    "m.daocloud.io/docker.io/library/redis:7.4-alpine",
    "m.daocloud.io/docker.io/minio/minio:RELEASE.2025-09-07T16-13-09Z",
    "m.daocloud.io/docker.io/minio/mc:RELEASE.2025-08-13T08-35-41Z",
    "m.daocloud.io/docker.io/library/nginx:1.28-alpine",
)


class ContainerMirrorTest(unittest.TestCase):
    def test_all_required_mirror_tags_have_manifests(self) -> None:
        for image in IMAGES:
            with self.subTest(image=image):
                result = subprocess.run(
                    ["docker", "manifest", "inspect", image],
                    text=True,
                    capture_output=True,
                    check=False,
                    timeout=90,
                )
                self.assertEqual(result.returncode, 0, f"{image}\n{result.stdout}\n{result.stderr}")

    def test_fallback_pulls_official_and_tags_preferred_name(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            log = root / "docker.log"
            fake = bin_dir / "docker"
            fake.write_text(
                """#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' \"$*\" >> \"${DOCKER_LOG}\"
if [[ \"$1 $2\" == \"image inspect\" ]]; then exit 1; fi
if [[ \"$1\" == \"pull\" && \"$2\" == m.daocloud.io/* ]]; then exit 1; fi
exit 0
""",
                encoding="utf-8",
            )
            fake.chmod(fake.stat().st_mode | stat.S_IXUSR)
            env = os.environ.copy()
            env["PATH"] = f"{bin_dir}:{env['PATH']}"
            env["DOCKER_LOG"] = str(log)
            mirror = "m.daocloud.io/docker.io/library/mysql:8.4"
            official = "docker.io/library/mysql:8.4"
            result = subprocess.run(
                ["bash", str(PULL), mirror],
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            calls = log.read_text(encoding="utf-8").splitlines()
            self.assertIn(f"pull {mirror}", calls)
            self.assertIn(f"pull {official}", calls)
            self.assertIn(f"tag {official} {mirror}", calls)


if __name__ == "__main__":
    unittest.main()
