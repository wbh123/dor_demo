#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
xml = (ROOT / 'backend-java/server/src/main/resources/mapper/admin/RoomManagementMapper.xml').read_text(encoding='utf-8')

# Every generated floor must satisfy the non-null dormitory_floor.floor_name column.
assert 'floor_name' in xml
assert 'batchInsertFloors' in xml
assert "CONCAT(#{floor}, '层')" in xml

print('Building floor name contract passed')
