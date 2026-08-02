#!/usr/bin/env python3
"""Materialize standalone 1000-student SQL files by inlining the shared base script."""

from __future__ import annotations

import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA_DIR = ROOT / "backend-java" / "docs" / "sql" / "test-data"
BASE_FILE = DATA_DIR / "1000_students_base.sql"
ENTRIES = {
    "clean": DATA_DIR / "1000_students_clean.sql",
    "realistic": DATA_DIR / "1000_students_realistic_mixed_state.sql",
}
SOURCE_LINE = "SOURCE 1000_students_base.sql;"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="生成不依赖MySQL SOURCE指令的千人测试数据单文件"
    )
    parser.add_argument(
        "--scenario",
        choices=("clean", "realistic", "all"),
        default="all",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=DATA_DIR / "generated",
    )
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def materialize(entry: Path, base: str) -> str:
    content = entry.read_text(encoding="utf-8")
    if content.count(SOURCE_LINE) != 1:
        raise ValueError(f"{entry} 必须且只能包含一次 {SOURCE_LINE}")
    banner = (
        "-- AUTO-GENERATED standalone SQL.\n"
        "-- Source files: 1000_students_base.sql + "
        f"{entry.name}\n\n"
    )
    return banner + content.replace(
        SOURCE_LINE,
        "-- BEGIN INLINED COMMON BASE\n"
        + base.rstrip()
        + "\n-- END INLINED COMMON BASE",
    )


def validate(sql: str, scenario: str) -> None:
    required = [
        "WHILE i<=1000",
        "WHILE building_index<=6",
        "DOMESTIC_ONLY",
        "INTERNATIONAL_ONLY",
        "MIXED",
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
    if SOURCE_LINE in sql:
        raise ValueError(f"{scenario} 独立文件仍包含SOURCE指令")


def main() -> int:
    args = parse_args()
    base = BASE_FILE.read_text(encoding="utf-8")
    scenarios = ENTRIES if args.scenario == "all" else {args.scenario: ENTRIES[args.scenario]}
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for scenario, entry in scenarios.items():
        rendered = materialize(entry, base)
        validate(rendered, scenario)
        output = args.output_dir / f"1000_students_{scenario}_standalone.sql"
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
