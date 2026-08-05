#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
service = (ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/admin/RoomManagementService.java').read_text(encoding='utf-8')

# Every generated floor must satisfy the non-null dormitory_floor.floor_name column.
assert 'floor_name' in service
assert 'floorName' in service
assert 'floor + "层"' in service

print('Building floor name contract passed')
