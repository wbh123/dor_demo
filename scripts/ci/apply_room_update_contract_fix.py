#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / "backend-java/model/src/main/resources/admin/openapi-admin.yaml"
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
SPEC.write_text(spec.replace(old_room_request, new_room_request, 1), encoding="utf-8")

contracts = CONTRACTS.read_text(encoding="utf-8")
line = "python scripts/ci/test_room_update_contract_sync.py\n"
if line not in contracts:
    anchor = "python scripts/ci/test_admin_building_room_contract_sync.py\n"
    if anchor not in contracts:
        raise RuntimeError("标准契约入口锚点不存在")
    contracts = contracts.replace(anchor, anchor + line, 1)
CONTRACTS.write_text(contracts, encoding="utf-8")

print("Room update OpenAPI contract fixed")
