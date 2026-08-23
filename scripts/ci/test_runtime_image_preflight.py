#!/usr/bin/env python3
from __future__ import annotations

import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PREPARE = ROOT / "scripts/ci/prepare_runtime_images_contract.sh"

ENV_CONTENT = """WUST_DORMITORY_MYSQL_IMAGE=mysql:8.4
WUST_DORMITORY_REDIS_IMAGE=redis:7.4-alpine
WUST_DORMITORY_MINIO_IMAGE=minio/minio:RELEASE.2025-09-07T16-13-09Z
WUST_DORMITORY_MINIO_MC_IMAGE=minio/mc:RELEASE.2025-08-13T08-35-41Z
WUST_DORMITORY_NGINX_IMAGE=nginx:1.28-alpine
"""


class RuntimeImagePreflightTest(unittest.TestCase):
    def run_prepare(self, *, release_mode: bool) -> list[str]:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            env_file = root / ".env"
            env_file.write_text(ENV_CONTENT, encoding="utf-8")
            bin_dir = root / "bin"
            bin_dir.mkdir()
            log = root / "docker.log"
            fake = bin_dir / "docker"
            fake.write_text(
                """#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$*" >> "${DOCKER_LOG}"
if [[ "$1 $2" == "image inspect" ]]; then exit 1; fi
exit 0
""",
                encoding="utf-8",
            )
            fake.chmod(fake.stat().st_mode | stat.S_IXUSR)
            env = os.environ.copy()
            env["PATH"] = f"{bin_dir}:{env['PATH']}"
            env["DOCKER_LOG"] = str(log)
            if release_mode:
                env["WUST_DORMITORY_PREPARE_SKIP_NGINX"] = "1"
            else:
                env.pop("WUST_DORMITORY_PREPARE_SKIP_NGINX", None)
                env.pop("WUST_DORMITORY_PREPARE_SKIP_JAVA", None)
            result = subprocess.run(
                ["bash", str(PREPARE), str(env_file)],
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            return log.read_text(encoding="utf-8").splitlines()

    def test_source_mode_preflights_nginx_and_java_runtime(self) -> None:
        calls = self.run_prepare(release_mode=False)
        self.assertIn("pull m.daocloud.io/docker.io/library/mysql:8.4", calls)
        self.assertIn("pull m.daocloud.io/docker.io/library/nginx:1.28-alpine", calls)
        self.assertIn("pull m.daocloud.io/docker.io/library/eclipse-temurin:21-jre-jammy", calls)

    def test_release_mode_skips_build_only_nginx_and_java_base_images(self) -> None:
        calls = self.run_prepare(release_mode=True)
        self.assertIn("pull m.daocloud.io/docker.io/library/mysql:8.4", calls)
        self.assertFalse(any("library/nginx:1.28-alpine" in call for call in calls))
        self.assertFalse(any("library/eclipse-temurin:21-jre-jammy" in call for call in calls))


if __name__ == "__main__":
    unittest.main()
