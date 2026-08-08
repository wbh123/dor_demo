from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
service = (ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/admin/AdminStudentResidencyAdjustmentService.java').read_text(encoding='utf-8')
mapper_java = (ROOT / 'backend-java/server/src/main/java/com/wust/dormitory/admin/AdminResidencyAdjustmentMapper.java').read_text(encoding='utf-8')
mapper_xml = (ROOT / 'backend-java/server/src/main/resources/mapper/admin/AdminResidencyAdjustmentMapper.xml').read_text(encoding='utf-8')

# Missing current residency must stay nullable; a magic -1 must not leak into SQL semantics.
assert 'currentRoomId == null ? -1L : currentRoomId' not in service, 'current room must stay nullable instead of using -1 sentinel'
assert '@Param("currentRoomId") Long currentRoomId' in mapper_java, 'mapper must accept nullable currentRoomId'

# Capacity is based only on ACTIVE residency facts.
assert "resident.assignment_status='ACTIVE'" in mapper_xml

# A currently placed student can swap into an occupied bed even when the target room is full.
assert '#{currentBedId,jdbcType=BIGINT} IS NOT NULL' in mapper_xml
assert "occupancy_source IN ('RESIDENCY','ALLOCATION')" in mapper_xml

# A student without a current bed must not be offered an occupied target as selectable/swap-required.
assert 'current_bed_present' in mapper_xml, 'selectability must distinguish first assignment from a real bed swap'
assert "current_bed_present=1" in mapper_xml

print('Residency adjustment context contract passed')
