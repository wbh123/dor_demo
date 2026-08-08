from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
mapper = (ROOT / 'backend-java/server/src/main/resources/mapper/admin/AdminDashboardMapper.xml').read_text(encoding='utf-8')
normalized = ' '.join(mapper.split())

assert 'FROM room_assignment' in mapper, 'dashboard must count current room_assignment facts'
assert "assignment_status = 'ACTIVE'" in mapper or "assignment_status='ACTIVE'" in mapper, 'dashboard count must filter ACTIVE residency'
assert 'SELECT COUNT(*) FROM bed_assignment) AS active_assignment_count' not in normalized, 'historical bed_assignment rows must not drive current resident count'
print('Dashboard active residency contract passed')
