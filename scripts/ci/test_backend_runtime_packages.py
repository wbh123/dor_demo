#!/usr/bin/env python3
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path


class BackendRuntimePackageTest(unittest.TestCase):
    def test_temurin_jammy_installs_required_runtime_tools_and_gosu_numeric_uid(self) -> None:
        dockerfile = """
FROM eclipse-temurin:21-jre-jammy
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \\
    && apt-get install -y --no-install-recommends \\
        ca-certificates curl git gosu mysql-client-core-8.0 python3 python3-pip redis-tools tini \\
    && rm -rf /var/lib/apt/lists/*
RUN java -version \\
    && python3 --version \\
    && mysql --version \\
    && mysqldump --version \\
    && redis-cli --version \\
    && git --version \\
    && gosu 12345:12345 sh -ec 'test "$(id -u)" = 12345 && test "$(id -g)" = 12345'
""".strip() + "\n"
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "Dockerfile").write_text(dockerfile, encoding="utf-8")
            completed = subprocess.run(
                ["docker", "build", "--pull", "-t", "wust-runtime-contract:test", "."],
                cwd=root,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(completed.returncode, 0, completed.stdout + completed.stderr)


if __name__ == "__main__":
    unittest.main()
