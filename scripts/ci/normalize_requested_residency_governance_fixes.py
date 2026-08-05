#!/usr/bin/env python3
from pathlib import Path

SCRIPT = Path(__file__).with_name("apply_requested_residency_governance_fixes.py")
text = SCRIPT.read_text(encoding="utf-8")

old_layout_anchor = '''    """                       bed.position_index, bed.layout_x, bed.layout_z, bed.rotation_degrees,
                       room.id AS room_id, room.room_number, room.room_type,
""",
    """                       bed.position_index, layout.layout_x, layout.layout_z, layout.rotation_degrees,
                       room.id AS room_id, room.room_number, room.room_type,
""",'''
new_layout_anchor = '''    "bed.position_index, bed.layout_x, bed.layout_z, bed.rotation_degrees",
    "bed.position_index, layout.layout_x, layout.layout_z, layout.rotation_degrees",'''
if text.count(old_layout_anchor) != 1:
    raise RuntimeError(f"住宿布局锚点数量异常：{text.count(old_layout_anchor)}")
text = text.replace(old_layout_anchor, new_layout_anchor, 1)

lines = text.splitlines(keepends=True)
fixed_audit_literal = 0
for index, line in enumerate(lines):
    stripped = line.strip()
    if stripped.startswith("'<div v-if=\"canAuditQuery\"") and "selectedAudit.error_code" in stripped:
        if not stripped.endswith("',"):
            raise RuntimeError("高级审计替换文本结尾格式异常")
        lines[index] = '    """' + stripped[1:-2] + '""",\n'
        fixed_audit_literal += 1
if fixed_audit_literal != 1:
    raise RuntimeError(f"高级审计字符串修正数量异常：{fixed_audit_literal}")
text = "".join(lines)

old_action_fallback = "?? key.replaceAll('_',' ').toLowerCase() || '完成业务操作'"
new_action_fallback = "?? (key ? key.replaceAll('_',' ').toLowerCase() : '完成业务操作')"
if text.count(old_action_fallback) != 1:
    raise RuntimeError(f"业务操作回退表达式数量异常：{text.count(old_action_fallback)}")
text = text.replace(old_action_fallback, new_action_fallback, 1)

SCRIPT.write_text(text, encoding="utf-8")
print("requested fix script normalized")
