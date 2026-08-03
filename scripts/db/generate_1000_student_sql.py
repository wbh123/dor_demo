#!/usr/bin/env python3
"""生成不依赖 SOURCE 的千人测试数据 SQL，默认适配 Navicat。"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT / "backend-java" / "docs" / "sql" / "test-data"
DEFAULT_OUTPUT_DIR = ROOT / "backend-java" / "docs" / "sql" / "navicat" / "generated"
DEFAULT_DATABASE_NAME = "wust_dormitory"
BASE_FILE = DATA_DIR / "1000_students_base.sql"
ENTRIES = {
    "clean": DATA_DIR / "1000_students_clean.sql",
    "realistic": DATA_DIR / "1000_students_realistic_mixed_state.sql",
}
SOURCE_LINE = "SOURCE 1000_students_base.sql;"
DATABASE_NAME_PATTERN = re.compile(r"^[A-Za-z0-9_]+$")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="生成 Navicat 可直接导入的千人测试数据 SQL")
    parser.add_argument("--scenario", choices=("clean", "realistic", "all"), default="all")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--database-name", default=DEFAULT_DATABASE_NAME)
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def validate_database_name(database_name: str) -> str:
    if not DATABASE_NAME_PATTERN.fullmatch(database_name):
        raise ValueError("数据库名称只能包含英文字母、数字和下划线")
    return database_name


def materialize(entry: Path, base: str, database_name: str) -> str:
    database_name = validate_database_name(database_name)
    content = entry.read_text(encoding="utf-8")
    if content.count(SOURCE_LINE) != 1:
        raise ValueError(f"{entry} 必须且只能包含一次 {SOURCE_LINE}")
    scenario_name = "1000人干净测试数据" if entry.name == "1000_students_clean.sql" else "1000人真实业务状态数据"
    banner = (
        "-- ============================================================\n"
        f"-- {scenario_name}\n"
        "-- Navicat 独立导入脚本\n"
        f"-- 统一数据库：{database_name}\n"
        "-- 警告：执行前会清空该数据库中的学校业务数据。\n"
        "-- 平台功能目录、套餐、订阅、系统设置和唯一系统管理员会保留。\n"
        "-- ============================================================\n\n"
        "SET NAMES utf8mb4;\n"
        f"USE `{database_name}`;\n"
        "SET @database_name = DATABASE();\n\n"
    )
    return banner + content.replace(
        SOURCE_LINE,
        "-- BEGIN INLINED COMMON BASE\n"
        + base.rstrip()
        + "\n-- END INLINED COMMON BASE",
    )


def validate(sql: str, scenario: str, database_name: str) -> None:
    required = [
        f"USE `{database_name}`;",
        "CALL clear_1000_test_data();",
        "WHILE i<=1000",
        "WHILE building_index<=6",
        "DOMESTIC_ONLY",
        "INTERNATIONAL_ONLY",
        "MIXED",
        "'system_setting'",
        "STUDENT_WELCOME_MESSAGE",
        "UPDATE matching_weight_scheme",
        "UPDATE batch_rule_template",
        "COUNT(*) FROM student",
        "COUNT(*) FROM room",
        "COUNT(*) FROM bed",
    ]
    if scenario == "clean":
        required.extend(["CLEAN_1000_READY", "COUNT(*) FROM selection_batch) <> 0"])
    else:
        required.extend(
            [
                "REALISTIC_1000_READY",
                "'OPEN','ROOM',0",
                "'OPEN','BED',1",
                "active_batch_room_lock",
                "room_assignment",
                "student_feature",
            ]
        )
    missing = [token for token in required if token not in sql]
    if missing:
        raise ValueError(f"{scenario} 场景缺少关键内容：{', '.join(missing)}")
    if SOURCE_LINE in sql or "SOURCE " in sql:
        raise ValueError(f"{scenario} 独立文件仍包含 SOURCE 指令")
    if "DROP DATABASE" in sql:
        raise ValueError(f"{scenario} 数据脚本不得删除数据库，只能清空业务数据")


def output_name(scenario: str) -> str:
    return "02_1000人干净测试数据.sql" if scenario == "clean" else "03_1000人真实业务数据.sql"


def main() -> int:
    args = parse_args()
    database_name = validate_database_name(args.database_name)
    base = BASE_FILE.read_text(encoding="utf-8")
    scenarios = ENTRIES if args.scenario == "all" else {args.scenario: ENTRIES[args.scenario]}
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for scenario, entry in scenarios.items():
        rendered = materialize(entry, base, database_name)
        validate(rendered, scenario, database_name)
        output = args.output_dir / output_name(scenario)
        if args.check:
            if not output.exists() or output.read_text(encoding="utf-8") != rendered:
                raise SystemExit(f"OUTDATED: {output}")
            print(f"OK: {output.relative_to(ROOT)}")
        else:
            output.write_text(rendered, encoding="utf-8", newline="\n")
            print(f"WROTE: {output.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
