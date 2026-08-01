#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MIGRATION_DIR = (
    REPO_ROOT / "backend-java/server/src/main/resources/db/migration"
)
DEFAULT_OUTPUT = REPO_ROOT / "backend-java/docs/sql/schema.sql"

VERSION_PATTERN = re.compile(r"^V(?P<version>[0-9][0-9_.]*)__(?P<description>.+)\.sql$")


def version_key(path: Path) -> tuple[int, ...]:
    match = VERSION_PATTERN.match(path.name)
    if match is None:
        raise ValueError(f"不是合法的 Flyway 版本迁移文件：{path.name}")
    return tuple(
        int(part)
        for part in re.split(r"[_.]", match.group("version"))
        if part
    )


def discover_migrations(directory: Path) -> list[Path]:
    migrations = [
        path
        for path in directory.glob("V*.sql")
        if VERSION_PATTERN.match(path.name)
    ]
    if not migrations:
        raise RuntimeError(f"未找到版本化迁移：{directory}")
    return sorted(migrations, key=version_key)


def build_baseline(migrations: list[Path]) -> str:
    header = [
        "-- ============================================================",
        "-- 武汉科技大学学生宿舍智能选择系统",
        "-- 数据库固化基线 SQL",
        "--",
        "-- 生成方式：python scripts/db/build_frozen_baseline.py",
        "-- 开发期间 Flyway 迁移是唯一事实来源；本文件是便于部署和审阅的合并快照。",
        "-- 第一阶段冻结后重新运行脚本并将输出纳入验收记录。",
        "-- 不包含 src/test/resources 下的开发测试数据。",
        "-- ============================================================",
        "",
    ]
    body: list[str] = []
    for migration in migrations:
        body.extend(
            [
                f"-- >>> BEGIN {migration.name}",
                migration.read_text(encoding="utf-8").rstrip(),
                f"-- <<< END {migration.name}",
                "",
            ]
        )
    return "\n".join(header + body).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="将已冻结的 Flyway 版本迁移合并为独立部署 SQL。"
    )
    parser.add_argument(
        "--migration-dir",
        type=Path,
        default=DEFAULT_MIGRATION_DIR,
        help=f"版本迁移目录，默认：{DEFAULT_MIGRATION_DIR}",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=DEFAULT_OUTPUT,
        help=f"输出文件，默认：{DEFAULT_OUTPUT}",
    )
    args = parser.parse_args()
    migrations = discover_migrations(args.migration_dir)
    baseline = build_baseline(migrations)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(baseline, encoding="utf-8")
    print(
        f"已将 {len(migrations)} 个 Flyway 版本迁移固化到 {args.output}。"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
