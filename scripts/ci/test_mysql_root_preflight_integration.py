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

ENV_CONTENT = """WUST_DORMITORY_DB_ROOT_PASSWORD=root-contract-pass
WUST_DORMITORY_MYSQL_IMAGE=mysql:8.4
WUST_DORMITORY_REDIS_IMAGE=redis:7.4-alpine
WUST_DORMITORY_MINIO_IMAGE=minio/minio:test
WUST_DORMITORY_MINIO_MC_IMAGE=minio/mc:test
WUST_DORMITORY_NGINX_IMAGE=nginx:1.28-alpine
"""


class MysqlRootPreflightIntegrationTest(unittest.TestCase):
    def run_prepare(self, filename: str) -> tuple[subprocess.CompletedProcess[str], Path]:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        env_file = root / filename
        env_file.write_text(ENV_CONTENT, encoding="utf-8")
        bin_dir = root / "bin"
        bin_dir.mkdir()
        fake = bin_dir / "docker"
        fake.write_text(
            """#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == "inspect" && "$2" == "wust-dormitory-mysql" ]]; then exit 1; fi
if [[ "$1 $2" == "image inspect" ]]; then exit 0; fi
if [[ "$1" == "pull" || "$1" == "tag" ]]; then exit 0; fi
exit 0
""",
            encoding="utf-8",
        )
        fake.chmod(fake.stat().st_mode | stat.S_IXUSR)
        env = os.environ.copy()
        env["PATH"] = f"{bin_dir}:{env['PATH']}"
        result = subprocess.run(["bash", str(PREPARE), str(env_file)], text=True, capture_output=True, check=False, env=env)
        return result, root

    def test_real_env_runs_guard_and_persists_hash_state(self) -> None:
        result, root = self.run_prepare(".env")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        state = root / "data/deploy/mysql-root-state.env"
        self.assertTrue(state.exists())
        self.assertIn("MYSQL_ROOT_PASSWORD_SHA256=", state.read_text(encoding="utf-8"))

    def test_example_env_skips_database_state_guard(self) -> None:
        result, root = self.run_prepare(".env.example")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse((root / "data/deploy/mysql-root-state.env").exists())


if __name__ == "__main__":
    unittest.main()
