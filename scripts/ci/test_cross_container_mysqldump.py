#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import time
import unittest
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SYNC_SCRIPT = ROOT / "scripts/ci/mysql_account_sync_contract.sh"


def run(*command: str, check: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(list(command), text=True, capture_output=True, check=check)


class CrossContainerMysqldumpTest(unittest.TestCase):
    def test_runtime_container_dumps_mysql_after_account_rotation(self) -> None:
        suffix = uuid.uuid4().hex[:10]
        network = f"wust-dump-{suffix}"
        mysql_container = f"wust-mysql-{suffix}"
        root_password = "root-pass-for-ci"
        old_app_password = "app-old-pass-for-ci"
        new_app_password = "app-new-pass-for-ci"
        backup_password = "backup-pass-for-ci"
        try:
            run("docker", "network", "create", network, check=True)
            run(
                "docker", "run", "-d", "--name", mysql_container, "--network", network,
                "-e", f"MYSQL_ROOT_PASSWORD={root_password}",
                "-e", "MYSQL_DATABASE=wust_contract",
                "-e", "MYSQL_USER=wust_app",
                "-e", f"MYSQL_PASSWORD={old_app_password}",
                "mysql:8.4",
                check=True,
            )
            for _ in range(60):
                ready = run(
                    "docker", "exec", "-e", f"MYSQL_PWD={root_password}", mysql_container,
                    "mysqladmin", "ping", "-h", "127.0.0.1", "-uroot", "--silent",
                )
                if ready.returncode == 0:
                    break
                time.sleep(2)
            else:
                self.fail(run("docker", "logs", mysql_container).stdout)

            sql = "CREATE TABLE sample(id INT PRIMARY KEY, name VARCHAR(32)); INSERT INTO sample VALUES(1,'ok');"
            seeded = subprocess.run(
                ["docker", "exec", "-i", "-e", f"MYSQL_PWD={root_password}", mysql_container,
                 "mysql", "-uroot", "wust_contract"],
                input=sql,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(seeded.returncode, 0, seeded.stderr)

            synchronized = run(
                "docker", "run", "--rm", "--network", network,
                "-v", f"{SYNC_SCRIPT}:/sync.sh:ro",
                "-e", f"MYSQL_HOST={mysql_container}",
                "-e", f"MYSQL_ROOT_PASSWORD={root_password}",
                "-e", "MYSQL_DATABASE=wust_contract",
                "-e", "WUST_DORMITORY_DB_USER=wust_app",
                "-e", f"WUST_DORMITORY_DB_PASSWORD={new_app_password}",
                "-e", "WUST_DORMITORY_BACKUP_DB_USER=wust_backup",
                "-e", f"WUST_DORMITORY_BACKUP_DB_PASSWORD={backup_password}",
                "mysql:8.4", "bash", "/sync.sh",
            )
            self.assertEqual(synchronized.returncode, 0, synchronized.stderr)

            old_login = run(
                "docker", "run", "--rm", "--network", network,
                "-e", f"MYSQL_PWD={old_app_password}",
                "mysql:8.4", "mysql", "--protocol=TCP", "--host", mysql_container,
                "--user", "wust_app", "wust_contract", "-e", "SELECT 1",
            )
            self.assertNotEqual(old_login.returncode, 0, "old application password must stop working after rotation")

            new_write = run(
                "docker", "run", "--rm", "--network", network,
                "-e", f"MYSQL_PWD={new_app_password}",
                "mysql:8.4", "mysql", "--protocol=TCP", "--host", mysql_container,
                "--user", "wust_app", "wust_contract", "-e",
                "INSERT INTO sample VALUES(2,'rotated')",
            )
            self.assertEqual(new_write.returncode, 0, new_write.stderr)

            backup_write = run(
                "docker", "run", "--rm", "--network", network,
                "-e", f"MYSQL_PWD={backup_password}",
                "mysql:8.4", "mysql", "--protocol=TCP", "--host", mysql_container,
                "--user", "wust_backup", "wust_contract", "-e",
                "INSERT INTO sample VALUES(3,'forbidden')",
            )
            self.assertNotEqual(backup_write.returncode, 0, "backup account must remain read-only")

            dumped = run(
                "docker", "run", "--rm", "--network", network,
                "-e", f"MYSQL_PWD={backup_password}",
                "wust-runtime-contract:test",
                "mysqldump", "--protocol=TCP", "--host", mysql_container, "--port", "3306",
                "--user", "wust_backup", "--single-transaction", "--quick", "--skip-lock-tables",
                "--triggers", "--hex-blob", "--no-tablespaces", "--set-gtid-purged=OFF",
                "--default-character-set=utf8mb4", "wust_contract",
            )
            self.assertEqual(dumped.returncode, 0, dumped.stderr)
            self.assertIn("CREATE TABLE `sample`", dumped.stdout)
            self.assertIn("INSERT INTO `sample` VALUES", dumped.stdout)
            self.assertIn("rotated", dumped.stdout)
        finally:
            run("docker", "rm", "-f", mysql_container)
            run("docker", "network", "rm", network)


if __name__ == "__main__":
    unittest.main()
