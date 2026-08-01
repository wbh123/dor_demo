#!/usr/bin/env python3
from __future__ import annotations

import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


class InfrastructureConfigurationTest(unittest.TestCase):
    def test_compose_only_defines_mysql_and_redis_with_healthchecks(self) -> None:
        compose = (REPO_ROOT / "docker/docker-compose.yml").read_text(encoding="utf-8")
        service_names = re.findall(r"^  ([a-z0-9-]+):\n", compose, flags=re.MULTILINE)
        self.assertEqual(service_names[:2], ["mysql", "redis"])
        self.assertNotIn("backend", service_names)
        self.assertNotIn("frontend", service_names)
        self.assertGreaterEqual(compose.count("healthcheck:"), 2)
        self.assertIn("../data/mysql:/var/lib/mysql", compose)
        self.assertIn("../data/redis:/data", compose)

    def test_env_example_contains_required_infrastructure_variables(self) -> None:
        content = (REPO_ROOT / ".env.example").read_text(encoding="utf-8")
        keys = {
            line.split("=", 1)[0]
            for line in content.splitlines()
            if line and not line.startswith("#") and "=" in line
        }
        required = {
            "WUST_DORMITORY_TIMEZONE",
            "WUST_DORMITORY_SERVER_PORT",
            "WUST_DORMITORY_MYSQL_IMAGE",
            "WUST_DORMITORY_DB_HOST",
            "WUST_DORMITORY_DB_PORT",
            "WUST_DORMITORY_DB_NAME",
            "WUST_DORMITORY_DB_USER",
            "WUST_DORMITORY_DB_PASSWORD",
            "WUST_DORMITORY_DB_ROOT_PASSWORD",
            "WUST_DORMITORY_REDIS_IMAGE",
            "WUST_DORMITORY_REDIS_HOST",
            "WUST_DORMITORY_REDIS_PORT",
            "WUST_DORMITORY_REDIS_PASSWORD",
        }
        self.assertTrue(required.issubset(keys), required - keys)

    def test_spring_configuration_uses_env_and_contains_no_plaintext_password(self) -> None:
        application = (
            REPO_ROOT / "backend-java/starter/src/main/resources/application.yaml"
        ).read_text(encoding="utf-8")
        self.assertIn("optional:file:./.env[.properties]", application)
        self.assertIn("${WUST_DORMITORY_DB_PASSWORD}", application)
        self.assertIn("${WUST_DORMITORY_REDIS_PASSWORD}", application)
        self.assertNotIn("change-me", application)
        self.assertNotIn("username: root", application)

    def test_server_declares_redis_starter(self) -> None:
        pom = (REPO_ROOT / "backend-java/server/pom.xml").read_text(encoding="utf-8")
        self.assertIn("spring-boot-starter-data-redis", pom)

    def test_runtime_ci_executes_full_http_smoke_flow(self) -> None:
        workflow = (REPO_ROOT / ".github/workflows/phase1-ci.yml").read_text(encoding="utf-8")
        smoke = REPO_ROOT / "scripts/e2e/phase1_smoke.py"
        self.assertTrue(smoke.is_file())
        self.assertIn(
            "classpath:db/migration,filesystem:backend-java/server/src/test/resources/db/dev-migration",
            workflow,
        )
        self.assertIn("python scripts/e2e/phase1_smoke.py", workflow)

    def test_validate_env_accepts_valid_values_and_rejects_placeholder(self) -> None:
        script = REPO_ROOT / "scripts/dev/validate-env.sh"
        example = REPO_ROOT / ".env.example"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts/dev").mkdir(parents=True)
            shutil.copy2(script, root / "scripts/dev/validate-env.sh")
            shutil.copy2(example, root / ".env.example")
            valid = example.read_text(encoding="utf-8")
            valid = valid.replace("请替换为数据库密码", "dev-db-pass-123")
            valid = valid.replace("请替换为数据库根密码", "dev-root-pass-123")
            valid = valid.replace("请替换为Redis密码", "dev-redis-pass-123")
            (root / ".env").write_text(valid, encoding="utf-8")

            success = subprocess.run(
                ["bash", str(root / "scripts/dev/validate-env.sh")],
                cwd=root,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(success.returncode, 0, success.stderr)
            self.assertIn("配置校验通过", success.stdout)

            (root / ".env").write_text(example.read_text(encoding="utf-8"), encoding="utf-8")
            failure = subprocess.run(
                ["bash", str(root / "scripts/dev/validate-env.sh")],
                cwd=root,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertNotEqual(failure.returncode, 0)
            self.assertIn("模板占位值", failure.stderr)

    def test_start_script_invokes_validator_through_bash(self) -> None:
        script = (REPO_ROOT / "scripts/dev/start-infra.sh").read_text(encoding="utf-8")
        self.assertIn('bash "${validate_script}"', script)
        direct_invocation = re.compile(r'^\s*"\$\{validate_script\}"\s*$', re.MULTILINE)
        self.assertNotRegex(script, direct_invocation)

    def test_restart_recreates_services_to_apply_env_changes(self) -> None:
        script = (REPO_ROOT / "scripts/dev/start-infra.sh").read_text(encoding="utf-8")
        self.assertIn("compose up -d --force-recreate mysql redis", script)
        self.assertNotIn("compose restart mysql redis", script)

    def test_shell_scripts_have_valid_bash_syntax(self) -> None:
        for relative in (
            "scripts/dev/validate-env.sh",
            "scripts/dev/start-infra.sh",
            "scripts/dev/reset-local-environment.sh",
        ):
            result = subprocess.run(
                ["bash", "-n", str(REPO_ROOT / relative)],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, f"{relative}: {result.stderr}")

    def test_phase_documents_mark_phase1_final_and_phase2_as_current(self) -> None:
        agents = (REPO_ROOT / "AGENTS.md").read_text(encoding="utf-8")
        root_readme = (REPO_ROOT / "README.md").read_text(encoding="utf-8")
        docs_readme = (REPO_ROOT / "docs/README.md").read_text(encoding="utf-8")
        phase_index = (REPO_ROOT / "docs/03_开发阶段/README.md").read_text(encoding="utf-8")
        phase1 = (REPO_ROOT / "docs/03_开发阶段/01_第一阶段/README.md").read_text(encoding="utf-8")
        freeze = (REPO_ROOT / "docs/03_开发阶段/01_第一阶段/07_第一阶段冻结说明.md").read_text(encoding="utf-8")

        for content in (agents, root_readme, docs_readme, phase_index):
            self.assertIn("第一阶段：已完成并最终冻结", content)
            self.assertIn("第二阶段：开发中", content)

        for content in (root_readme, phase_index, phase1, freeze):
            self.assertIn("V4__refine_questionnaire_and_active_batch_rules.sql", content)
            self.assertIn("邀请式组队", content)
            self.assertIn("Three.js", content)

        self.assertIn("bash scripts/dev/reset-local-environment.sh", root_readme)
        self.assertNotIn(
            "WUST_DORMITORY_FLYWAY_LOCATIONS=classpath:db/migration,filesystem:backend-java/server/src/test/resources/db/dev-migration",
            root_readme,
        )


if __name__ == "__main__":
    unittest.main()
