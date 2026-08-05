#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
service = (ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java').read_text(encoding='utf-8')

assert 'floor_name' in service
assert 'floorName' in service

print('Building floor name contract passed')
