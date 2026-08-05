#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
path = root / "scripts/ci/validate_system_contracts.py"
text = path.read_text(encoding="utf-8")
old = '''    require(
        "恢复标准2×2布局" in editor
        and "new DefaultPlacement(-2.35, -1.65, 0)" in read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
        ),
        "standard horizontal 2x2 default layout is not preserved",
        errors,
    )
'''
new = '''    require(
        "恢复标准2×2布局" not in editor
        and "new DefaultPlacement(-2.35, -1.65, 0)" in read(
            "backend-java/server/src/main/java/com/wust/dormitory/admin/RoomLayoutService.java"
        ),
        "standard horizontal 2x2 backend default layout is not preserved or the removed reset button returned",
        errors,
    )
'''
if old not in text:
    raise SystemExit("layout contract target not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("Updated default-layout contract")
