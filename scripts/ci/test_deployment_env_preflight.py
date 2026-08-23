#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VALIDATE = ROOT / "scripts/ci/validate_deployment_env_contract.sh"

BASE = """WUST_DORMITORY_DB_NAME=wust_dormitory
WUST_DORMITORY_DB_USER=wust_app
WUST_DORMITORY_BACKUP_DB_USER=wust_backup
WUST_DORMITORY_DB_PUBLISHED_PORT=3306
WUST_DORMITORY_REDIS_PUBLISHED_PORT=6379
WUST_DORMITORY_MINIO_API_PORT=19000
WUST_DORMITORY_MINIO_CONSOLE_PORT=19001
WUST_DORMITORY_BACKEND_PUBLISHED_PORT=8080
WUST_DORMITORY_NGINX_PORT=80
"""


class DeploymentEnvPreflightTest(unittest.TestCase):
    def run_validation(self, content: str) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text(content, encoding="utf-8")
            return subprocess.run(["bash", str(VALIDATE), str(env_file)], text=True, capture_output=True, check=False)

    def test_valid_published_ports_and_identifiers_pass(self) -> None:
        result = self.run_validation(BASE)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_duplicate_published_port_is_rejected_before_docker_start(self) -> None:
        result = self.run_validation(BASE.replace("WUST_DORMITORY_BACKEND_PUBLISHED_PORT=8080", "WUST_DORMITORY_BACKEND_PUBLISHED_PORT=80"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("port", result.stderr.lower())
        self.assertIn("WUST_DORMITORY_NGINX_PORT", result.stderr)
        self.assertIn("WUST_DORMITORY_BACKEND_PUBLISHED_PORT", result.stderr)

    def test_invalid_database_identifier_is_rejected_early(self) -> None:
        result = self.run_validation(BASE.replace("WUST_DORMITORY_DB_NAME=wust_dormitory", "WUST_DORMITORY_DB_NAME=wust-dormitory"))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("WUST_DORMITORY_DB_NAME", result.stderr)

    def test_database_accounts_must_be_distinct_and_non_root(self) -> None:
        same = self.run_validation(BASE.replace("WUST_DORMITORY_BACKUP_DB_USER=wust_backup", "WUST_DORMITORY_BACKUP_DB_USER=wust_app"))
        self.assertNotEqual(same.returncode, 0)
        root = self.run_validation(BASE.replace("WUST_DORMITORY_DB_USER=wust_app", "WUST_DORMITORY_DB_USER=root"))
        self.assertNotEqual(root.returncode, 0)

    def test_shell_syntax(self) -> None:
        result = subprocess.run(["bash", "-n", str(VALIDATE)], text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
