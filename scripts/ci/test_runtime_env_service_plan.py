#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / "scripts/ci/runtime_env_service_plan_contract.sh"

BASE_ENV = """WUST_DORMITORY_TIMEZONE=Asia/Shanghai
VITE_APP_TITLE=A
WUST_DORMITORY_MYSQL_IMAGE=mysql:8.4
WUST_DORMITORY_DB_PASSWORD=db-one
WUST_DORMITORY_DB_PUBLISHED_PORT=3306
WUST_DORMITORY_REDIS_IMAGE=redis:7.4-alpine
WUST_DORMITORY_REDIS_PASSWORD=redis-one
WUST_DORMITORY_REDIS_PUBLISHED_PORT=6379
WUST_DORMITORY_MINIO_IMAGE=minio/minio:test
WUST_DORMITORY_MINIO_MC_IMAGE=minio/mc:test
WUST_DORMITORY_MINIO_API_PORT=19000
WUST_DORMITORY_MINIO_CONSOLE_PORT=19001
WUST_DORMITORY_MINIO_ROOT_USER=root
WUST_DORMITORY_MINIO_ROOT_PASSWORD=root-pass
WUST_DORMITORY_MINIO_BACKUP_BUCKET=wust-backups
WUST_DORMITORY_OBJECT_STORAGE_ACCESS_KEY=app
WUST_DORMITORY_OBJECT_STORAGE_SECRET_KEY=app-secret
WUST_DORMITORY_BACKEND_PUBLISHED_PORT=8080
WUST_DORMITORY_CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:*
WUST_DORMITORY_NGINX_IMAGE=nginx:1.28-alpine
WUST_DORMITORY_NGINX_PORT=80
"""


def parse(output: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in output.splitlines():
        if "=" in line:
            key, value = line.split("=", 1)
            result[key] = value
    return result


class RuntimeEnvServicePlanTest(unittest.TestCase):
    def run_plan(self, env_file: Path, previous: dict[str, str] | None = None) -> dict[str, str]:
        previous = previous or {}
        args = [
            "bash",
            str(CONTRACT),
            str(env_file),
            previous.get("MYSQL_RUNTIME_ENV_SHA256", ""),
            previous.get("REDIS_RUNTIME_ENV_SHA256", ""),
            previous.get("MINIO_RUNTIME_ENV_SHA256", ""),
            previous.get("BACKEND_RUNTIME_ENV_SHA256", ""),
            previous.get("NGINX_RUNTIME_ENV_SHA256", ""),
        ]
        result = subprocess.run(args, text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        return parse(result.stdout)

    def snapshot(self, env_file: Path) -> dict[str, str]:
        plan = self.run_plan(env_file)
        return {key: value for key, value in plan.items() if key.endswith("_RUNTIME_ENV_SHA256")}

    def test_vite_only_change_restarts_no_runtime_service(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text(BASE_ENV, encoding="utf-8")
            previous = self.snapshot(env_file)
            env_file.write_text(BASE_ENV.replace("VITE_APP_TITLE=A", "VITE_APP_TITLE=B"), encoding="utf-8")
            plan = self.run_plan(env_file, previous)
            for key in ("MYSQL_CONFIG_CHANGED", "REDIS_CONFIG_CHANGED", "MINIO_CONFIG_CHANGED", "BACKEND_CONFIG_CHANGED", "NGINX_CONFIG_CHANGED"):
                self.assertEqual(plan[key], "0", key)

    def test_database_password_change_restarts_mysql_and_backend_only(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text(BASE_ENV, encoding="utf-8")
            previous = self.snapshot(env_file)
            env_file.write_text(BASE_ENV.replace("db-one", "db-two"), encoding="utf-8")
            plan = self.run_plan(env_file, previous)
            self.assertEqual(plan["MYSQL_CONFIG_CHANGED"], "1")
            self.assertEqual(plan["BACKEND_CONFIG_CHANGED"], "1")
            self.assertEqual(plan["REDIS_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["MINIO_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["NGINX_CONFIG_CHANGED"], "0")

    def test_redis_password_change_restarts_redis_and_backend_only(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text(BASE_ENV, encoding="utf-8")
            previous = self.snapshot(env_file)
            env_file.write_text(BASE_ENV.replace("redis-one", "redis-two"), encoding="utf-8")
            plan = self.run_plan(env_file, previous)
            self.assertEqual(plan["REDIS_CONFIG_CHANGED"], "1")
            self.assertEqual(plan["BACKEND_CONFIG_CHANGED"], "1")
            self.assertEqual(plan["MYSQL_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["MINIO_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["NGINX_CONFIG_CHANGED"], "0")

    def test_minio_bucket_change_restarts_minio_init_and_backend_only(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text(BASE_ENV, encoding="utf-8")
            previous = self.snapshot(env_file)
            env_file.write_text(BASE_ENV.replace("wust-backups", "wust-backups-v2"), encoding="utf-8")
            plan = self.run_plan(env_file, previous)
            self.assertEqual(plan["MINIO_CONFIG_CHANGED"], "1")
            self.assertEqual(plan["BACKEND_CONFIG_CHANGED"], "1")
            self.assertEqual(plan["MYSQL_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["REDIS_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["NGINX_CONFIG_CHANGED"], "0")

    def test_nginx_port_change_restarts_only_nginx(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text(BASE_ENV, encoding="utf-8")
            previous = self.snapshot(env_file)
            env_file.write_text(BASE_ENV.replace("WUST_DORMITORY_NGINX_PORT=80", "WUST_DORMITORY_NGINX_PORT=8088"), encoding="utf-8")
            plan = self.run_plan(env_file, previous)
            self.assertEqual(plan["NGINX_CONFIG_CHANGED"], "1")
            self.assertEqual(plan["MYSQL_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["REDIS_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["MINIO_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["BACKEND_CONFIG_CHANGED"], "0")

    def test_backend_only_config_change_restarts_only_backend(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            env_file = Path(temporary) / ".env"
            env_file.write_text(BASE_ENV, encoding="utf-8")
            previous = self.snapshot(env_file)
            env_file.write_text(BASE_ENV.replace("http://localhost:*", "https://example.edu"), encoding="utf-8")
            plan = self.run_plan(env_file, previous)
            self.assertEqual(plan["BACKEND_CONFIG_CHANGED"], "1")
            self.assertEqual(plan["MYSQL_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["REDIS_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["MINIO_CONFIG_CHANGED"], "0")
            self.assertEqual(plan["NGINX_CONFIG_CHANGED"], "0")

    def test_shell_syntax(self) -> None:
        result = subprocess.run(["bash", "-n", str(CONTRACT)], text=True, capture_output=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == "__main__":
    unittest.main()
