from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path('private-repo')
JAVA_ROOT = ROOT / 'backend-java/server/src/main/java'
BASELINE = ROOT / 'scripts/ci/backend_modularization_baseline.json'

current = json.loads(BASELINE.read_text(encoding='utf-8'))
java_line_limit = int(current['java_line_limit'])
controller_operation_limit = int(current['controller_operation_limit'])

override_pattern = re.compile(r'(?m)^\s*@Override\s*$')
route_pattern = re.compile(r'(?m)^\s*@(Get|Post|Put|Delete|Patch)Mapping\b')


def rel(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def lines(path: Path) -> int:
    return len(path.read_text(encoding='utf-8').splitlines())


def operations(path: Path) -> int:
    source = path.read_text(encoding='utf-8')
    override_count = len(override_pattern.findall(source))
    return override_count if override_count > 0 else len(route_pattern.findall(source))


java_files = sorted(JAVA_ROOT.rglob('*.java'))
large_java = {
    rel(path): lines(path)
    for path in java_files
    if lines(path) > java_line_limit
}
controller_files = [
    path for path in java_files
    if path.name.endswith('Controller.java') and '@RestController' in path.read_text(encoding='utf-8')
]
large_controllers = {
    rel(path): operations(path)
    for path in controller_files
    if operations(path) > controller_operation_limit
}

snapshot = {
    'java_line_limit': java_line_limit,
    'controller_operation_limit': controller_operation_limit,
    'large_java_files': dict(sorted(large_java.items())),
    'large_controllers': dict(sorted(large_controllers.items())),
}
BASELINE.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(
    f"synced clean-head modularity baseline: {len(large_java)} large Java files, "
    f"{len(large_controllers)} aggregate controllers"
)
