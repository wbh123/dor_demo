from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path

ROOT = Path('private-repo')
BASELINE = ROOT / 'scripts/ci/backend_modularization_baseline.json'
JAVA_PREFIX = 'backend-java/server/src/main/java'
BASE_REF = 'origin/main'

current = json.loads(BASELINE.read_text(encoding='utf-8'))
java_line_limit = int(current['java_line_limit'])
controller_operation_limit = int(current['controller_operation_limit'])

override_pattern = re.compile(r'(?m)^\s*@Override\s*$')
route_pattern = re.compile(r'(?m)^\s*@(Get|Post|Put|Delete|Patch)Mapping\b')


def git(*args: str) -> str:
    return subprocess.run(
        ['git', *args], cwd=ROOT, check=True, text=True, capture_output=True
    ).stdout


def source_at(path: str) -> str:
    return git('show', f'{BASE_REF}:{path}')


paths = [
    path for path in git('ls-tree', '-r', '--name-only', BASE_REF, '--', JAVA_PREFIX).splitlines()
    if path.endswith('.java')
]
sources = {path: source_at(path) for path in paths}

large_java = {
    path: len(source.splitlines())
    for path, source in sources.items()
    if len(source.splitlines()) > java_line_limit
}
large_controllers = {}
for path, source in sources.items():
    if not path.endswith('Controller.java') or '@RestController' not in source:
        continue
    override_count = len(override_pattern.findall(source))
    operation_count = override_count if override_count > 0 else len(route_pattern.findall(source))
    if operation_count > controller_operation_limit:
        large_controllers[path] = operation_count

snapshot = {
    'java_line_limit': java_line_limit,
    'controller_operation_limit': controller_operation_limit,
    'large_java_files': dict(sorted(large_java.items())),
    'large_controllers': dict(sorted(large_controllers.items())),
}
BASELINE.write_text(json.dumps(snapshot, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(
    f"synced modularity baseline from {BASE_REF}: {len(large_java)} large Java files, "
    f"{len(large_controllers)} aggregate controllers"
)
