#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
GENERATOR_PATH = REPO_ROOT / "scripts/db/build_frozen_baseline.py"
MIGRATION_DIR = REPO_ROOT / "backend-java/server/src/main/resources/db/migration"
FROZEN_SCHEMA = REPO_ROOT / "backend-java/docs/sql/schema.sql"


def load_generator():
    spec = importlib.util.spec_from_file_location("build_frozen_baseline", GENERATOR_PATH)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载数据库固化脚本：{GENERATOR_PATH}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


class FrozenSchemaTest(unittest.TestCase):
    def test_frozen_schema_matches_versioned_migrations(self) -> None:
        generator = load_generator()
        expected = generator.build_baseline(generator.discover_migrations(MIGRATION_DIR))
        actual = FROZEN_SCHEMA.read_text(encoding="utf-8")
        self.assertEqual(
            actual,
            expected,
            "固化schema.sql与Flyway迁移不一致，请运行python scripts/db/build_frozen_baseline.py",
        )


if __name__ == "__main__":
    unittest.main()
