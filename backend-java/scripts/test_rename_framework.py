#!/usr/bin/env python3
"""rename-framework.py 的标准库回归测试。"""

from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_CONTENT = Path(__file__).with_name("rename-framework.py").read_text(encoding="utf-8")


class RenameFrameworkTest(unittest.TestCase):
    def test_config_driven_rename_and_move(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            (root / "server/src/main/java/com/service/demo").mkdir(parents=True)
            (root / "starter/src/main/java/com/service/demo").mkdir(parents=True)
            (root / "server/src/main/resources/com/service/demo/mappers").mkdir(parents=True)

            (root / "scripts/rename-framework.py").write_text(SCRIPT_CONTENT, encoding="utf-8")
            (root / "pom.xml").write_text(
                "<groupId>com.service.demo</groupId><artifactId>ServiceDemo</artifactId>",
                encoding="utf-8",
            )
            (root / "server/src/main/java/com/service/demo/Example.java").write_text(
                "package com.service.demo;",
                encoding="utf-8",
            )
            (root / "starter/src/main/java/com/service/demo/StartApplication.java").write_text(
                "package com.service.demo; public class StartApplication {}",
                encoding="utf-8",
            )
            (root / "server/src/main/resources/com/service/demo/mappers/Test.xml").write_text(
                "com/service/demo",
                encoding="utf-8",
            )

            config = {
                "schema_version": 1,
                "project_root": ".",
                "text": {
                    "roots": ["pom.xml", "server", "starter"],
                    "exclude_dirs": ["target"],
                    "exclude_files": ["scripts/rename-framework.py"],
                    "include_extensions": [".xml", ".java"],
                    "include_names": [],
                },
                "replacements": [
                    {
                        "from": "<groupId>com.service.demo</groupId>",
                        "to": "<groupId>org.urbansafe</groupId>",
                    },
                    {
                        "from": "<artifactId>ServiceDemo</artifactId>",
                        "to": "<artifactId>urban-safe-priority-server</artifactId>",
                    },
                    {"from": "StartApplication", "to": "UrbanSafePriorityApplication"},
                    {"from": "com.service.demo", "to": "org.urbansafe.priority"},
                    {"from": "com/service/demo", "to": "org/urbansafe/priority"},
                ],
                "moves": [
                    {
                        "source": "server/src/main/java/com/service/demo",
                        "target": "server/src/main/java/org/urbansafe/priority",
                    },
                    {
                        "source": "starter/src/main/java/com/service/demo/StartApplication.java",
                        "target": "starter/src/main/java/org/urbansafe/priority/UrbanSafePriorityApplication.java",
                    },
                    {
                        "source": "server/src/main/resources/com/service/demo",
                        "target": "server/src/main/resources/org/urbansafe/priority",
                    },
                ],
            }
            (root / "scripts/rename-framework.json").write_text(
                json.dumps(config, ensure_ascii=False),
                encoding="utf-8",
            )

            result = subprocess.run(
                [sys.executable, str(root / "scripts/rename-framework.py")],
                cwd=root,
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertTrue(
                (root / "server/src/main/java/org/urbansafe/priority/Example.java").is_file()
            )
            self.assertTrue(
                (
                    root
                    / "starter/src/main/java/org/urbansafe/priority/UrbanSafePriorityApplication.java"
                ).is_file()
            )
            self.assertTrue(
                (
                    root
                    / "server/src/main/resources/org/urbansafe/priority/mappers/Test.xml"
                ).is_file()
            )
            self.assertIn("org.urbansafe", (root / "pom.xml").read_text(encoding="utf-8"))

    def test_absolute_config_path_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            (root / "scripts").mkdir()
            (root / "scripts/rename-framework.py").write_text(SCRIPT_CONTENT, encoding="utf-8")
            result = subprocess.run(
                [
                    sys.executable,
                    str(root / "scripts/rename-framework.py"),
                    "--config",
                    str((root / "outside.json").resolve()),
                ],
                cwd=root,
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("必须是工程根目录内的相对路径", result.stderr)


if __name__ == "__main__":
    unittest.main()
