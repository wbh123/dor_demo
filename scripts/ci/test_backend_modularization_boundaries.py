#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "backend-java" / "server" / "src" / "main" / "java"
BASELINE_PATH = ROOT / "scripts" / "ci" / "backend_modularization_baseline.json"
BASELINE = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))

JAVA_LINE_LIMIT = int(BASELINE["java_line_limit"])
CONTROLLER_OPERATION_LIMIT = int(BASELINE["controller_operation_limit"])
EXPECTED_LARGE_JAVA = {
    str(path): int(limit) for path, limit in BASELINE["large_java_files"].items()
}
EXPECTED_LARGE_CONTROLLERS = {
    str(path): int(limit) for path, limit in BASELINE["large_controllers"].items()
}

OVERRIDE_PATTERN = re.compile(r"(?m)^\s*@Override\s*$")
ROUTE_PATTERN = re.compile(r"(?m)^\s*@(Get|Post|Put|Delete|Patch)Mapping\b")


def line_count(path: Path) -> int:
    return len(path.read_text(encoding="utf-8").splitlines())


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def controller_operation_count(path: Path) -> int:
    source = path.read_text(encoding="utf-8")
    override_count = len(OVERRIDE_PATTERN.findall(source))
    route_count = len(ROUTE_PATTERN.findall(source))
    return override_count if override_count > 0 else route_count


java_files = sorted(JAVA_ROOT.rglob("*.java"))
large_java = {
    relative(path): line_count(path)
    for path in java_files
    if line_count(path) > JAVA_LINE_LIMIT
}

controller_files = [
    path
    for path in java_files
    if path.name.endswith("Controller.java")
    and "@RestController" in path.read_text(encoding="utf-8")
]
large_controllers = {
    relative(path): controller_operation_count(path)
    for path in controller_files
    if controller_operation_count(path) > CONTROLLER_OPERATION_LIMIT
}

errors: list[str] = []

for path, lines in large_java.items():
    maximum = EXPECTED_LARGE_JAVA.get(path)
    if maximum is None:
        errors.append(f"新增或未登记的大型Java文件：{path}（当前 {lines} 行）")
    elif lines > maximum:
        errors.append(f"大型Java文件继续增长：{path}（当前 {lines}，基线 {maximum}）")
for path in EXPECTED_LARGE_JAVA:
    if path not in large_java:
        errors.append(f"大型Java文件已低于阈值，请删除基线项：{path}")

for path, operations in large_controllers.items():
    maximum = EXPECTED_LARGE_CONTROLLERS.get(path)
    if maximum is None:
        errors.append(
            f"新增或未登记的聚合Controller：{path}（当前 {operations} 个operation）"
        )
    elif operations > maximum:
        errors.append(
            f"聚合Controller operation继续增长：{path}（当前 {operations}，基线 {maximum}）"
        )
for path in EXPECTED_LARGE_CONTROLLERS:
    if path not in large_controllers:
        errors.append(f"Controller已低于operation阈值，请删除基线项：{path}")

candidate = {
    "java_line_limit": JAVA_LINE_LIMIT,
    "controller_operation_limit": CONTROLLER_OPERATION_LIMIT,
    "large_java_files": dict(sorted(large_java.items())),
    "large_controllers": dict(sorted(large_controllers.items())),
}

if errors:
    print("后端模块化基线不匹配：")
    for error in errors:
        print(f"- {error}")
    print("\nBASELINE_CANDIDATE=")
    print(json.dumps(candidate, ensure_ascii=False, indent=2))
    raise SystemExit(1)

print(
    "backend modularization boundaries: OK "
    f"({len(large_java)} large Java files, "
    f"{len(large_controllers)} aggregate controllers)"
)
