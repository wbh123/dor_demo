#!/usr/bin/env python3
from __future__ import annotations

import re
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
MIGRATION = (
    REPO_ROOT
    / "backend-java/server/src/main/resources/db/migration/V1__create_phase1_schema.sql"
)


class MySqlCompatibilityTest(unittest.TestCase):
    def test_reserved_row_number_identifier_is_quoted(self) -> None:
        sql = MIGRATION.read_text(encoding="utf-8")
        self.assertIsNone(
            re.search(r"(?m)^\s*row_number\s+INT\b", sql),
            "MySQL 8将row_number视为保留关键字，列名必须使用反引号",
        )
        self.assertIn("`row_number` INT NOT NULL", sql)
        self.assertIn("(import_job_id, `row_number`)", sql)


if __name__ == "__main__":
    unittest.main()
