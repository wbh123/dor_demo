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


def repository_relative(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPO_ROOT.resolve()).as_posix()
    except ValueError as exception:
        raise ValueError(f"迁移文件必须位于仓库目录内：{path}") from exception


def build_source_entry(migrations: list[Path]) -> str:
    latest = version_key(migrations[-1])
    latest_text = ".".join(str(part) for part in latest)
    header = [
        "-- ============================================================",
        "-- 武汉科技大学学生宿舍智能选择系统",
        f"-- 数据库架构安装入口：Flyway V1～V{latest_text}",
        "--",
        "-- 生成方式：python scripts/db/build_frozen_baseline.py",
        "-- 正式Flyway版本迁移是数据库结构的唯一事实来源。",
        "-- 本文件使用MySQL客户端SOURCE命令按版本顺序执行全部正式迁移，",
        "-- 从而避免复制型schema.sql在新增迁移后长期未更新。",
        "--",
        "-- 必须从仓库根目录执行，例如：",
        "--   mysql --binary-mode=1 -u<user> -p <database> < backend-java/docs/sql/schema.sql",
        "--",
        "-- 注意：SOURCE是MySQL客户端命令，本文件不能作为JDBC单条SQL执行。",
        "-- 生产升级仍应优先使用Flyway，不应重复导入本文件。",
        "-- ============================================================",
        "",
        "SET NAMES utf8mb4;",
        "",
    ]
    body: list[str] = []
    for migration in migrations:
        relative = repository_relative(migration)
        body.extend(
            [
                f"-- V{'.'.join(str(part) for part in version_key(migration))}: {migration.name}",
                f"SOURCE {relative}",
                "",
            ]
        )
    return "\n".join(header + body).rstrip() + "\n"


def build_inline_snapshot(migrations: list[Path]) -> str:
    header = [
        "-- ============================================================",
        "-- 武汉科技大学学生宿舍智能选择系统",
        "-- 数据库独立内联快照 SQL",
        "--",
        "-- 生成方式：python scripts/db/build_frozen_baseline.py --mode inline",
        "-- 正式Flyway版本迁移仍是唯一事实来源。",
        "-- 不包含src/test/resources下的开发测试数据。",
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


def build_baseline(migrations: list[Path]) -> str:
    """兼容旧测试入口，默认生成可执行的SOURCE架构安装入口。"""
    return build_source_entry(migrations)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="根据正式Flyway迁移生成数据库架构安装入口或独立内联快照。"
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
    parser.add_argument(
        "--mode",
        choices=("source", "inline"),
        default="source",
        help="source生成轻量安装入口；inline生成完整独立快照",
    )
    args = parser.parse_args()
    migrations = discover_migrations(args.migration_dir)
    output = (
        build_source_entry(migrations)
        if args.mode == "source"
        else build_inline_snapshot(migrations)
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(output, encoding="utf-8")
    latest = ".".join(str(part) for part in version_key(migrations[-1]))
    print(
        f"已根据{len(migrations)}个Flyway版本迁移生成V{latest}数据库架构文件：{args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
