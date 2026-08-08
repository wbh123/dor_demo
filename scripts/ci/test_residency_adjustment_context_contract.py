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
assert 'active_resident_count' in mapper_xml

# Swap eligibility is derived from whether the source student really has a current bed.
assert 'CASE WHEN #{currentBedId,jdbcType=BIGINT} IS NULL THEN 0 ELSE 1 END AS current_bed_present' in mapper_xml
assert "current_bed_present=1" in mapper_xml
assert "occupancy_source IN ('RESIDENCY','ALLOCATION')" in mapper_xml

# Full target rooms remain reachable for real swaps, while first assignment cannot select occupied beds.
assert 'active_resident_count &lt; compatible_beds.capacity' in mapper_xml
assert "compatible_beds.current_bed_present=1 AND compatible_beds.occupancy_source IN ('RESIDENCY','ALLOCATION')" in mapper_xml
assert "compatible_beds.occupancy_source='AVAILABLE'" in mapper_xml

print('Residency adjustment context contract passed')
