#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MIGRATION_DIR = REPO_ROOT / "backend-java/server/src/main/resources/db/migration"
DEFAULT_OUTPUT = REPO_ROOT / "backend-java/docs/sql/schema.sql"
DEFAULT_DATABASE_NAME = "wust_dormitory"

VERSION_PATTERN = re.compile(r"^V(?P<version>[0-9][0-9_.]*)__(?P<description>.+)\.sql$")
DATABASE_NAME_PATTERN = re.compile(r"^[A-Za-z0-9_]+$")


def version_key(path: Path) -> tuple[int, ...]:
    match = VERSION_PATTERN.match(path.name)
    if match is None:
        raise ValueError(f"不是合法的 Flyway 版本迁移文件：{path.name}")
    return tuple(int(part) for part in re.split(r"[_.]", match.group("version")) if part)


def discover_migrations(directory: Path) -> list[Path]:
    migrations = [path for path in directory.glob("V*.sql") if VERSION_PATTERN.match(path.name)]
    if not migrations:
        raise RuntimeError(f"未找到版本化迁移：{directory}")
    return sorted(migrations, key=version_key)


def repository_relative(path: Path) -> str:
    try:
        return path.resolve().relative_to(REPO_ROOT.resolve()).as_posix()
    except ValueError as exception:
        raise ValueError(f"迁移文件必须位于仓库目录内：{path}") from exception


def latest_version_text(migrations: list[Path]) -> str:
    return ".".join(str(part) for part in version_key(migrations[-1]))


def validate_database_name(database_name: str) -> str:
    if not DATABASE_NAME_PATTERN.fullmatch(database_name):
        raise ValueError("数据库名称只能包含英文字母、数字和下划线")
    return database_name


def inline_migrations(migrations: list[Path]) -> list[str]:
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
    return body


def build_source_entry(migrations: list[Path]) -> str:
    latest = latest_version_text(migrations)
    header = [
        "-- ============================================================",
        "-- 武汉科技大学学生宿舍智能选择系统",
        f"-- Schema version: V1-V{latest}",
        f"-- 数据库架构安装入口：Flyway V1～V{latest}",
        "--",
        "-- 生成方式：python scripts/db/build_frozen_baseline.py",
        "-- 正式 Flyway 迁移是数据库结构的唯一事实来源。",
        "-- 本文件使用 MySQL 客户端 SOURCE 命令。",
        "-- Navicat 请使用 backend-java/docs/sql/navicat 目录。",
        "-- ============================================================",
        "",
        "SET NAMES utf8mb4;",
        "",
    ]
    body: list[str] = []
    for migration in migrations:
        body.extend(
            [
                f"-- V{'.'.join(str(part) for part in version_key(migration))}: {migration.name}",
                f"SOURCE {repository_relative(migration)}",
                "",
            ]
        )
    return "\n".join(header + body).rstrip() + "\n"


def build_inline_snapshot(migrations: list[Path]) -> str:
    latest = latest_version_text(migrations)
    header = [
        "-- ============================================================",
        "-- 武汉科技大学学生宿舍智能选择系统",
        f"-- Schema version: V1-V{latest}",
        "-- 数据库独立内联快照 SQL",
        "-- 正式 Flyway 迁移仍是唯一事实来源。",
        "-- ============================================================",
        "",
    ]
    return "\n".join(header + inline_migrations(migrations)).rstrip() + "\n"


def build_flyway_baseline(latest: str) -> list[str]:
    return [
        "-- 为 Navicat 全量建库建立 Flyway 基线，后续 V17 及更高版本可继续迁移。",
        "DROP TABLE IF EXISTS flyway_schema_history;",
        "CREATE TABLE flyway_schema_history (",
        "    installed_rank INT NOT NULL,",
        "    version VARCHAR(50) NULL,",
        "    description VARCHAR(200) NOT NULL,",
        "    type VARCHAR(20) NOT NULL,",
        "    script VARCHAR(1000) NOT NULL,",
        "    checksum INT NULL,",
        "    installed_by VARCHAR(100) NOT NULL,",
        "    installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,",
        "    execution_time INT NOT NULL,",
        "    success TINYINT(1) NOT NULL,",
        "    PRIMARY KEY (installed_rank),",
        "    INDEX flyway_schema_history_s_idx (success)",
        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;",
        "INSERT INTO flyway_schema_history",
        "(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success)",
        f"VALUES (1,'{latest}','Navicat full schema baseline','BASELINE','<< Flyway Baseline >>',NULL,SUBSTRING_INDEX(CURRENT_USER(),'@',1),0,1);",
        "",
    ]


def build_navicat_schema(migrations: list[Path], database_name: str) -> str:
    database_name = validate_database_name(database_name)
    latest = latest_version_text(migrations)
    header = [
        "-- ============================================================",
        "-- 武汉科技大学学生宿舍智能选择系统",
        f"-- Navicat 独立数据库架构脚本：V1-V{latest}",
        f"-- 统一数据库：{database_name}",
        "-- 警告：执行后会删除同名数据库及其中全部数据。",
        "-- ============================================================",
        "",
        "SET NAMES utf8mb4;",
        "SET FOREIGN_KEY_CHECKS = 0;",
        f"DROP DATABASE IF EXISTS `{database_name}`;",
        f"CREATE DATABASE `{database_name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;",
        f"USE `{database_name}`;",
        "SET FOREIGN_KEY_CHECKS = 1;",
        "",
    ]
    footer = build_flyway_baseline(latest) + [
        f"USE `{database_name}`;",
        "SELECT DATABASE() AS current_database,",
        f"       'V1-V{latest}' AS schema_version,",
        "       'NAVICAT_SCHEMA_READY' AS status;",
        "",
    ]
    return "\n".join(header + inline_migrations(migrations) + footer).rstrip() + "\n"


def build_baseline(migrations: list[Path]) -> str:
    """兼容旧测试入口，默认生成 SOURCE 架构入口。"""
    return build_source_entry(migrations)


def main() -> int:
    parser = argparse.ArgumentParser(description="生成数据库架构入口、内联快照或 Navicat 独立脚本。")
    parser.add_argument("--migration-dir", type=Path, default=DEFAULT_MIGRATION_DIR)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--mode", choices=("source", "inline", "navicat"), default="source")
    parser.add_argument("--database-name", default=DEFAULT_DATABASE_NAME)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    migrations = discover_migrations(args.migration_dir)
    if args.mode == "source":
        rendered = build_source_entry(migrations)
    elif args.mode == "inline":
        rendered = build_inline_snapshot(migrations)
    else:
        rendered = build_navicat_schema(migrations, args.database_name)

    if args.check:
        if not args.output.exists() or args.output.read_text(encoding="utf-8") != rendered:
            raise SystemExit(f"OUTDATED: {args.output}")
        print(f"OK: {args.output.relative_to(REPO_ROOT)}")
        return 0

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(rendered, encoding="utf-8", newline="\n")
    print(
        f"已根据 {len(migrations)} 个 Flyway 迁移生成 V{latest_version_text(migrations)} 文件：{args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
