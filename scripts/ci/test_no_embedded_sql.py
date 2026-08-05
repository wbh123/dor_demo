#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import re
import subprocess
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_PREFIX = "backend-java/server/src/main/java"
BASELINE_FILE = ROOT / "scripts/ci/embedded_sql_baseline.txt"

SQL_ANNOTATION = re.compile(
    r"@\s*(Select|Insert|Update|Delete)\s*\((?P<body>.*?)\)",
    re.IGNORECASE | re.DOTALL,
)
TEXT_BLOCK = re.compile(r'"""(?P<body>.*?)"""', re.DOTALL)
SQL_TEXT = re.compile(
    r"\b(?:SELECT\b|INSERT\s+INTO\b|UPDATE\s+[A-Za-z_`]|DELETE\s+FROM\b|"
    r"MERGE\s+INTO\b|WITH\s+[A-Za-z_][A-Za-z0-9_]*\s+AS\s*\()",
    re.IGNORECASE,
)


def run_git(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=ROOT,
        check=check,
        text=True,
        capture_output=True,
    )


def normalize(value: str) -> str:
    return " ".join(value.split())


def fingerprint(kind: str, body: str) -> str:
    normalized = f"{kind}:{normalize(body)}"
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def sql_fingerprints(source: str) -> set[str]:
    result = {
        fingerprint(f"annotation:{match.group(1).lower()}", match.group("body"))
        for match in SQL_ANNOTATION.finditer(source)
    }
    for match in TEXT_BLOCK.finditer(source):
        body = match.group("body")
        if SQL_TEXT.search(body):
            result.add(fingerprint("text-block", body))
    return result


def self_test() -> None:
    old = 'var sql = """ SELECT id FROM student WHERE id=:id """;'
    same = 'var sql = """\n SELECT   id FROM student WHERE id=:id\n """;'
    removed = "return Map.of();"
    added = 'var sql = """ UPDATE student SET enabled=1 """;'
    baseline = sql_fingerprints(old)
    assert sql_fingerprints(same) == baseline
    assert sql_fingerprints(removed).issubset(baseline)
    assert not sql_fingerprints(added).issubset(baseline)
    assert sql_fingerprints('@Select("SELECT id FROM student")')


def baseline_revision() -> str:
    revision = BASELINE_FILE.read_text(encoding="utf-8").strip()
    if not re.fullmatch(r"[0-9a-f]{40}", revision):
        raise RuntimeError("embedded_sql_baseline.txt 必须包含一个完整的 40 位提交摘要")
    if run_git("cat-file", "-e", f"{revision}^{{commit}}", check=False).returncode != 0:
        fetched = run_git(
            "fetch",
            "--no-tags",
            "--depth=1",
            "origin",
            revision,
            check=False,
        )
        if fetched.returncode != 0:
            raise RuntimeError(
                "无法读取内嵌 SQL 基线提交。请执行："
                f"git fetch --no-tags --depth=1 origin {revision}\n"
                + fetched.stderr.strip()
            )
    return revision


def baseline_sources(revision: str) -> dict[str, str]:
    listed = run_git(
        "ls-tree",
        "-r",
        "--name-only",
        revision,
        "--",
        JAVA_PREFIX,
    ).stdout.splitlines()
    sources: dict[str, str] = {}
    for path in listed:
        if path.endswith(".java"):
            sources[path] = run_git("show", f"{revision}:{path}").stdout
    return sources


def current_sources() -> dict[str, str]:
    root = ROOT / JAVA_PREFIX
    return {
        path.relative_to(ROOT).as_posix(): path.read_text(encoding="utf-8")
        for path in root.rglob("*.java")
    }


def main() -> int:
    self_test()
    revision = baseline_revision()
    baseline = baseline_sources(revision)
    current = current_sources()
    violations: dict[str, set[str]] = defaultdict(set)

    for path, source in current.items():
        current_fingerprints = sql_fingerprints(source)
        allowed_fingerprints = sql_fingerprints(baseline.get(path, ""))
        new_fingerprints = current_fingerprints - allowed_fingerprints
        if new_fingerprints:
            violations[path].update(new_fingerprints)

    if violations:
        print("Java 内嵌 SQL 门禁失败：检测到基线之后新增或修改的 SQL。")
        for path, fingerprints in sorted(violations.items()):
            short = ", ".join(sorted(value[:12] for value in fingerprints))
            print(f"- {path}: {short}")
        print("请把 SQL 移入 MyBatis XML Mapper；不得前移基线以绕过单个变更。")
        return 1

    baseline_count = sum(len(sql_fingerprints(source)) for source in baseline.values())
    current_count = sum(len(sql_fingerprints(source)) for source in current.values())
    print(
        "Java 内嵌 SQL 门禁通过："
        f"当前 {current_count} 个指纹均来自固定基线 {revision[:12]}，"
        f"基线共 {baseline_count} 个指纹。"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
