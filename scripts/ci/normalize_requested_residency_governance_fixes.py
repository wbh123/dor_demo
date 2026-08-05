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

policy_start = text.index("# 3. 选寝策略采用")
matching_start = text.index('matching = "frontend/src/views/admin/AdminMatchingView.vue"', policy_start)
current_policy_patch = '''# 3. 选寝策略按当前服务结构采用 update-then-ensure；页面保留未展示的第三项策略值。
replace_once(
    "backend-java/server/src/main/java/com/wust/dormitory/selection/SelectionPolicyService.java",
    """    private void write(String key, boolean value) {
        jdbc.update(\"UPDATE system_setting SET setting_value=:value,version=version+1 WHERE setting_key=:key\",
                Map.of(\"key\", key, \"value\", Boolean.toString(value)));
    }
""",
    """    private void write(String key, boolean value) {
        int updated = jdbc.update(
                \"UPDATE system_setting SET setting_value=:value,version=version+1 WHERE setting_key=:key\",
                Map.of(\"key\", key, \"value\", Boolean.toString(value)));
        if (updated == 0) {
            ensure(key, value);
        }
    }
""",
)
'''
text = text[:policy_start] + current_policy_patch + text[matching_start:]

room_change_start = text.index('room_change = "frontend/src/views/student/StudentRoomChangeView.vue"')
residency_start = text.index('residency = "frontend/src/views/admin/AdminResidencyView.vue"', room_change_start)
current_room_change_patch = r"""room_change = "frontend/src/views/student/StudentRoomChangeView.vue"
replace_once(room_change, "import { api }", "import AppModal from '../../components/modal/AppModal.vue'\nimport { api }")
sub_once(
    room_change,
    r'<div v-if="target" class="modal-overlay room-change-overlay" @click\.self="closeDialog"><section class="modal-card room-change-dialog" role="dialog" aria-modal="true">(.*?)</section></div>',
    r'''<AppModal :open="Boolean(target)" size="default" :busy="submitting" @close="closeDialog"><div v-if="target" class="room-change-dialog" role="dialog">\1</div></AppModal>''',
)

"""
text = text[:room_change_start] + current_room_change_patch + text[residency_start:]

modal_type_guards = {
    r'<div class="student-dialog">\1</div>': r'<div v-if="editingStudent" class="student-dialog">\1</div>',
    r'<div class="placement-dialog">\1</div>': r'<div v-if="placementTarget" class="placement-dialog">\1</div>',
    r'<div class="reset-dialog">\1</div>': r'<div v-if="resetTarget" class="reset-dialog">\1</div>',
    r'<div class="bed-confirm-dialog">\1</div>': r'<div v-if="selected" class="bed-confirm-dialog">\1</div>',
}
for old, new in modal_type_guards.items():
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"公共弹窗类型守卫锚点数量异常：{old} -> {count}")
    text = text.replace(old, new, 1)

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
