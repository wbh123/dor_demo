#!/usr/bin/env python3
# Trigger after workflow registration.
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / "backend-java/model/src/main/resources/admin/openapi-admin.yaml"
FRONTEND = ROOT / "frontend/src/views/admin/AdminDormitoryView.vue"
CONTRACTS = ROOT / "scripts/ci/run_contracts.sh"

spec = SPEC.read_text(encoding="utf-8")
old_room_request = """    RoomRequest:
      type: object
      required: [roomType, capacity, gender, operationalStatus, reason]
      properties:
        roomType:
          type: string
          pattern: '^(FOUR_PERSON|FIVE_PERSON|SIX_PERSON|OTHER)$'
        capacity: { type: integer, minimum: 1, maximum: 20 }
        gender: { type: string, pattern: '^[MF]$' }
        operationalStatus:
          type: string
          pattern: '^(ENABLED|DISABLED|MAINTENANCE)$'
        remark: { type: string, maxLength: 500 }
        reason: { type: string, minLength: 1, maxLength: 500 }
"""
new_room_request = """    RoomRequest:
      type: object
      required: [capacity, gender, educationLevelScope, residentScope, operationalStatus, reason]
      properties:
        capacity: { type: integer, minimum: 1, maximum: 20 }
        gender: { type: string, pattern: '^[MF]$' }
        educationLevelScope: { type: string, enum: [UNDERGRADUATE_ONLY, GRADUATE_ONLY, MIXED] }
        residentScope: { type: string, enum: [DOMESTIC_ONLY, INTERNATIONAL_ONLY, MIXED] }
        operationalStatus:
          type: string
          pattern: '^(ENABLED|DISABLED|MAINTENANCE)$'
        remark: { type: string, maxLength: 500 }
        reason: { type: string, minLength: 1, maxLength: 500 }
"""
if old_room_request not in spec:
    raise RuntimeError("RoomRequest 旧契约锚点不存在")
spec = spec.replace(old_room_request, new_room_request, 1)
SPEC.write_text(spec, encoding="utf-8")

frontend = FRONTEND.read_text(encoding="utf-8")
frontend = frontend.replace(
    "  gender: 'M' | 'F'\n  residentScope: 'DOMESTIC_ONLY' | 'INTERNATIONAL_ONLY' | 'MIXED'\n",
    "  gender: 'M' | 'F'\n  educationLevelScope: 'UNDERGRADUATE_ONLY' | 'GRADUATE_ONLY' | 'MIXED'\n  residentScope: 'DOMESTIC_ONLY' | 'INTERNATIONAL_ONLY' | 'MIXED'\n",
    1,
)
frontend = frontend.replace(
    "  capacity: 4, gender: 'F', residentScope: 'MIXED',\n",
    "  capacity: 4, gender: 'F', educationLevelScope: 'MIXED', residentScope: 'MIXED',\n",
    1,
)
frontend = frontend.replace(
    "  editForm.gender = String(room.gender_restriction) as RoomEditForm['gender']\n  editForm.residentScope = String(room.resident_scope ?? 'MIXED') as RoomEditForm['residentScope']\n",
    "  editForm.gender = String(room.gender_restriction) as RoomEditForm['gender']\n  editForm.educationLevelScope = String(room.education_level_scope ?? 'MIXED') as RoomEditForm['educationLevelScope']\n  editForm.residentScope = String(room.resident_scope ?? 'MIXED') as RoomEditForm['residentScope']\n",
    1,
)
form_anchor = """          <label><span>规划容量</span><input v-model.number=\"editForm.capacity\" class=\"input\" type=\"number\" readonly /></label><label><span>房间性别</span><select v-model=\"editForm.gender\" class=\"input\"><option value=\"M\">男寝</option><option value=\"F\">女寝</option></select></label>
          <div class=\"span-2\"><span class=\"field-label\">学生类别属性</span>"""
form_replacement = """          <label><span>规划容量</span><input v-model.number=\"editForm.capacity\" class=\"input\" type=\"number\" readonly /></label><label><span>房间性别</span><select v-model=\"editForm.gender\" class=\"input\"><option value=\"M\">男寝</option><option value=\"F\">女寝</option></select></label>
          <label class=\"span-2\"><span>学历层次</span><select v-model=\"editForm.educationLevelScope\" class=\"input\"><option value=\"UNDERGRADUATE_ONLY\">仅本科生</option><option value=\"GRADUATE_ONLY\">仅研究生</option><option value=\"MIXED\">本科生与研究生</option></select></label>
          <div class=\"span-2\"><span class=\"field-label\">学生类别属性</span>"""
if form_anchor not in frontend:
    raise RuntimeError("房间编辑表单锚点不存在")
frontend = frontend.replace(form_anchor, form_replacement, 1)
FRONTEND.write_text(frontend, encoding="utf-8")

contracts = CONTRACTS.read_text(encoding="utf-8")
line = "python scripts/ci/test_room_update_contract_sync.py\n"
if line not in contracts:
    anchor = "python scripts/ci/test_admin_building_room_contract_sync.py\n"
    if anchor not in contracts:
        raise RuntimeError("标准契约入口锚点不存在")
    contracts = contracts.replace(anchor, anchor + line, 1)
CONTRACTS.write_text(contracts, encoding="utf-8")

print("Room update OpenAPI and frontend contract fixed")
