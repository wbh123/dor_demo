#!/usr/bin/env python3
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FRONTEND = ROOT / "frontend" / "src"
BASELINE_PATH = ROOT / "scripts" / "ci" / "frontend_modularization_baseline.json"
BASELINE = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))

VIEW_LIMIT = int(BASELINE["view_effective_line_limit"])
LOGIC_LIMIT = int(BASELINE["logic_line_limit"])
EXPECTED_VIEWS = {str(path): int(limit) for path, limit in BASELINE["large_views"].items()}
EXPECTED_LOGIC = {str(path): int(limit) for path, limit in BASELINE["large_logic_files"].items()}
EXPECTED_NOCHECK = set(BASELINE["ts_nocheck_allowlist"])


def line_count(path: Path) -> int:
    return len(path.read_text(encoding="utf-8").splitlines())


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def effective_view_lines(view: Path) -> int:
    total = line_count(view)
    for suffix in (".logic.ts", ".template.html", ".css"):
        companion = view.with_name(f"{view.stem}{suffix}")
        if companion.exists():
            total += line_count(companion)
    return total


large_views = {
    relative(view): effective_view_lines(view)
    for view in sorted((FRONTEND / "views").rglob("*.vue"))
    if effective_view_lines(view) > VIEW_LIMIT
}

logic_candidates = set((FRONTEND / "views").rglob("*.logic.ts"))
logic_candidates.update((FRONTEND / "features").rglob("composables/*.ts"))
large_logic = {
    relative(path): line_count(path)
    for path in sorted(logic_candidates)
    if line_count(path) > LOGIC_LIMIT
}

ts_nocheck = {
    relative(path)
    for path in sorted(FRONTEND.rglob("*"))
    if path.is_file()
    and path.suffix in {".ts", ".vue"}
    and "@ts-nocheck" in path.read_text(encoding="utf-8")
}

errors: list[str] = []

for path, lines in large_views.items():
    maximum = EXPECTED_VIEWS.get(path)
    if maximum is None:
        errors.append(f"新增或未登记的大型页面：{path}（有效行数 {lines}）")
    elif lines > maximum:
        errors.append(f"大型页面继续增长：{path}（当前 {lines}，基线 {maximum}）")
for path in EXPECTED_VIEWS:
    if path not in large_views:
        errors.append(f"大型页面已低于阈值，请删除基线项：{path}")

for path, lines in large_logic.items():
    maximum = EXPECTED_LOGIC.get(path)
    if maximum is None:
        errors.append(f"新增或未登记的大型逻辑文件：{path}（行数 {lines}）")
    elif lines > maximum:
        errors.append(f"大型逻辑文件继续增长：{path}（当前 {lines}，基线 {maximum}）")
for path in EXPECTED_LOGIC:
    if path not in large_logic:
        errors.append(f"大型逻辑文件已低于阈值，请删除基线项：{path}")

new_nocheck = sorted(ts_nocheck - EXPECTED_NOCHECK)
stale_nocheck = sorted(EXPECTED_NOCHECK - ts_nocheck)
for path in new_nocheck:
    errors.append(f"新增未登记的 @ts-nocheck：{path}")
for path in stale_nocheck:
    errors.append(f"@ts-nocheck 已删除，请清理白名单：{path}")

if errors:
    print("前端模块化基线不匹配：")
    for error in errors:
        print(f"- {error}")
    print("\nBASELINE_CANDIDATE=")
    print(json.dumps({
        "view_effective_line_limit": VIEW_LIMIT,
        "logic_line_limit": LOGIC_LIMIT,
        "large_views": large_views,
        "large_logic_files": large_logic,
        "ts_nocheck_allowlist": sorted(ts_nocheck),
    }, ensure_ascii=False, indent=2))
    raise SystemExit(1)

print(
    "frontend modularization boundaries: OK "
    f"({len(large_views)} large views, {len(large_logic)} large logic files, "
    f"{len(ts_nocheck)} ts-nocheck files)"
)
