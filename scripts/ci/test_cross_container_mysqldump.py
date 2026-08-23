#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import time
import unittest
import uuid


def run(*command: str, check: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(list(command), text=True, capture_output=True, check=check)


class CrossContainerMysqldumpTest(unittest.TestCase):
    def test_runtime_container_dumps_mysql_over_docker_network(self) -> None:
        suffix = uuid.uuid4().hex[:10]
        network = f"wust-dump-{suffix}"
        mysql_container = f"wust-mysql-{suffix}"
        root_password = "root-pass-for-ci"
        backup_password = "backup-pass-for-ci"
        try:
            run("docker", "network", "create", network, check=True)
            run(
                "docker", "run", "-d", "--name", mysql_container, "--network", network,
                "-e", f"MYSQL_ROOT_PASSWORD={root_password}",
                "-e", "MYSQL_DATABASE=wust_contract",
                "-e", "MYSQL_USER=wust_backup",
                "-e", f"MYSQL_PASSWORD={backup_password}",
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
            seeded = run(
                "docker", "exec", "-i", "-e", f"MYSQL_PWD={root_password}", mysql_container,
                "mysql", "-uroot", "wust_contract",
            )
            # Re-run with stdin via subprocess directly because helper intentionally has no input argument.
            seeded = subprocess.run(
                ["docker", "exec", "-i", "-e", f"MYSQL_PWD={root_password}", mysql_container,
                 "mysql", "-uroot", "wust_contract"],
                input=sql,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(seeded.returncode, 0, seeded.stderr)

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
        finally:
            run("docker", "rm", "-f", mysql_container)
            run("docker", "network", "rm", network)


if __name__ == "__main__":
    unittest.main()
