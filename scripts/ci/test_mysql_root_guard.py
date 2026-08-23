#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import os
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GUARD = ROOT / "scripts/ci/mysql_root_guard_contract.sh"


def sha256(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


class MysqlRootGuardTest(unittest.TestCase):
    def make_fake_docker(self, root: Path, *, container_exists: bool, accepts_new_password: bool, container_password: str = "old-root") -> tuple[dict[str, str], Path]:
        bin_dir = root / "bin"
        bin_dir.mkdir()
        log = root / "docker.log"
        fake = bin_dir / "docker"
        fake.write_text(
            """#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$*" >> "${DOCKER_LOG}"
if [[ "$1" == "inspect" && "$2" == "wust-dormitory-mysql" && $# -eq 2 ]]; then
  [[ "${CONTAINER_EXISTS}" == "1" ]] && exit 0 || exit 1
fi
if [[ "$1" == "inspect" && "$2" == "--format" ]]; then
  printf 'MYSQL_ROOT_PASSWORD=%s\n' "${CONTAINER_PASSWORD}"
  exit 0
fi
if [[ "$1" == "exec" ]]; then
  [[ "${ACCEPTS_NEW_PASSWORD}" == "1" ]] && exit 0 || exit 1
fi
exit 1
""",
            encoding="utf-8",
        )
        fake.chmod(fake.stat().st_mode | stat.S_IXUSR)
        env = os.environ.copy()
        env["PATH"] = f"{bin_dir}:{env['PATH']}"
        env["DOCKER_LOG"] = str(log)
        env["CONTAINER_EXISTS"] = "1" if container_exists else "0"
        env["ACCEPTS_NEW_PASSWORD"] = "1" if accepts_new_password else "0"
        env["CONTAINER_PASSWORD"] = container_password
        return env, log

    def run_guard(self, *, previous_hash: str, new_password: str, data_nonempty: bool, container_exists: bool, accepts_new_password: bool) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            env_file = root / ".env"
            env_file.write_text(f"WUST_DORMITORY_DB_ROOT_PASSWORD={new_password}\n", encoding="utf-8")
            state_file = root / "state.env"
            state_file.write_text(f"MYSQL_ROOT_PASSWORD_SHA256={previous_hash}\n", encoding="utf-8")
            data_dir = root / "mysql-data"
            data_dir.mkdir()
            if data_nonempty:
                (data_dir / "ibdata1").write_text("x", encoding="utf-8")
            env, _ = self.make_fake_docker(root, container_exists=container_exists, accepts_new_password=accepts_new_password)
            return subprocess.run(
                ["bash", str(GUARD), str(env_file), str(state_file), str(data_dir)],
                text=True,
                capture_output=True,
                check=False,
                env=env,
            )

    def test_unchanged_root_hash_is_allowed_without_docker_probe(self) -> None:
        password = "same-root"
        result = self.run_guard(previous_hash=sha256(password), new_password=password, data_nonempty=True, container_exists=False, accepts_new_password=False)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn(f"CURRENT_MYSQL_ROOT_PASSWORD_SHA256={sha256(password)}", result.stdout)

    def test_changed_root_with_existing_data_and_missing_container_is_blocked(self) -> None:
        result = self.run_guard(previous_hash=sha256("old-root"), new_password="new-root", data_nonempty=True, container_exists=False, accepts_new_password=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("MySQL root", result.stderr)

    def test_changed_root_is_allowed_only_when_running_database_accepts_new_password(self) -> None:
        denied = self.run_guard(previous_hash=sha256("old-root"), new_password="new-root", data_nonempty=True, container_exists=True, accepts_new_password=False)
        self.assertNotEqual(denied.returncode, 0)
        allowed = self.run_guard(previous_hash=sha256("old-root"), new_password="new-root", data_nonempty=True, container_exists=True, accepts_new_password=True)
        self.assertEqual(allowed.returncode, 0, allowed.stdout + allowed.stderr)

    def test_fresh_empty_data_allows_new_root_password(self) -> None:
        result = self.run_guard(previous_hash="", new_password="new-root", data_nonempty=False, container_exists=False, accepts_new_password=False)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_shell_syntax(self) -> None:
        result = subprocess.run(["bash", "-n", str(GUARD)], text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
