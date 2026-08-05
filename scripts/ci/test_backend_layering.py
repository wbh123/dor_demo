#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "backend-java/server/src/main/java"

INFRASTRUCTURE_PATTERNS = {
    "Spring JDBC": re.compile(
        r"(?:import\s+org\.springframework\.jdbc\.|\b(?:JdbcTemplate|NamedParameterJdbcTemplate)\b)"
    ),
    "Spring Data Redis": re.compile(
        r"(?:import\s+org\.springframework\.data\.redis\.|\b(?:RedisTemplate|StringRedisTemplate)\b)"
    ),
    "业务 Mapper": re.compile(
        r"import\s+com\.wust\.dormitory\.[\w.]*\.mapper\."
    ),
}


def controller_sources() -> list[Path]:
    return sorted(
        path
        for path in JAVA_ROOT.rglob("*.java")
        if path.name.endswith("Controller.java")
        or "@RestController" in path.read_text(encoding="utf-8")
    )


def main() -> int:
    failures: list[str] = []
    controllers = controller_sources()
    for path in controllers:
        source = path.read_text(encoding="utf-8")
        for label, pattern in INFRASTRUCTURE_PATTERNS.items():
            if pattern.search(source):
                relative = path.relative_to(ROOT).as_posix()
                failures.append(f"{relative}: Controller 不得直接依赖 {label}")

    if failures:
        print("后端分层门禁失败：")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print(
        f"后端分层门禁通过：检查 {len(controllers)} 个 Controller，"
        "未发现 JDBC、Redis 或业务 Mapper 直连。"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
