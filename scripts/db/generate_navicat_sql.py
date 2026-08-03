#!/usr/bin/env python3
"""一次生成 Navicat 使用的数据库架构、两套千人测试数据和完整性检查。"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT_DIR = Path(__file__).resolve().parent
NAVICAT_DIR = ROOT / "backend-java" / "docs" / "sql" / "navicat"
OUTPUT_DIR = NAVICAT_DIR / "generated"
INTEGRITY_SOURCE = NAVICAT_DIR / "04_数据库完整性检查" / "00_修复并检查数据库完整性.sql"
INTEGRITY_OUTPUT = OUTPUT_DIR / "04_数据库完整性检查.sql"
DATABASE_NAME = "wust_dormitory"


def run(command: list[str]) -> None:
    completed = subprocess.run(command, cwd=ROOT, check=False)
    if completed.returncode != 0:
        raise SystemExit(completed.returncode)


def sync_integrity_file(check: bool) -> None:
    expected = INTEGRITY_SOURCE.read_text(encoding="utf-8")
    if check:
        if not INTEGRITY_OUTPUT.exists() or INTEGRITY_OUTPUT.read_text(encoding="utf-8") != expected:
            raise SystemExit(f"OUTDATED: {INTEGRITY_OUTPUT}")
        print(f"OK: {INTEGRITY_OUTPUT.relative_to(ROOT)}")
        return
    shutil.copyfile(INTEGRITY_SOURCE, INTEGRITY_OUTPUT)
    print(f"WROTE: {INTEGRITY_OUTPUT.relative_to(ROOT)}")


def main() -> int:
    parser = argparse.ArgumentParser(description="生成 Navicat 四份独立 SQL 文件")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    check = ["--check"] if args.check else []
    run(
        [
            sys.executable,
            str(SCRIPT_DIR / "build_frozen_baseline.py"),
            "--mode",
            "navicat",
            "--database-name",
            DATABASE_NAME,
            "--output",
            str(OUTPUT_DIR / "01_数据库架构.sql"),
            *check,
        ]
    )
    run(
        [
            sys.executable,
            str(SCRIPT_DIR / "generate_1000_student_sql.py"),
            "--scenario",
            "all",
            "--database-name",
            DATABASE_NAME,
            "--output-dir",
            str(OUTPUT_DIR),
            *check,
        ]
    )
    sync_integrity_file(args.check)
    print(f"Navicat SQL 已准备：{OUTPUT_DIR.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
