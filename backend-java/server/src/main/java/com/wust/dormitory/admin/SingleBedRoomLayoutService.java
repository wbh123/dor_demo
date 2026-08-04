package com.wust.dormitory.admin;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Primary
public class SingleBedRoomLayoutService extends RoomLayoutService {
    public static final String SINGLE_BED = "SINGLE_BED";

    private final NamedParameterJdbcTemplate jdbc;

    public SingleBedRoomLayoutService(
            NamedParameterJdbcTemplate jdbc,
            AuditService auditService) {
        super(jdbc, auditService);
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Object> getLayout(long roomId) {
        Map<String, Object> source = super.getLayout(roomId);
        Map<String, Object> result = new LinkedHashMap<>(source);
        Set<Long> residentBedIds = new HashSet<>(jdbc.query("""
                SELECT bed_id
                FROM room_assignment
                WHERE room_id=:roomId
                  AND assignment_status='ACTIVE'
                  AND bed_id IS NOT NULL
                """, Map.of("roomId", roomId), (rs, rowNum) -> rs.getLong(1)));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sourceBeds = (List<Map<String, Object>>) source.getOrDefault("beds", List.of());
        List<Map<String, Object>> beds = sourceBeds.stream().map(item -> {
            Map<String, Object> bed = new LinkedHashMap<>(item);
            long bedId = ((Number) item.get("id")).longValue();
            if (residentBedIds.contains(bedId)) {
                bed.put("occupied", 1);
            }
            if (SINGLE_BED.equals(String.valueOf(item.get("bed_type")))) {
                bed.put("layout_unit_type", SINGLE_BED);
            }
            return bed;
        }).toList();
        result.put("beds", beds);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> updateLayout(
            long roomId,
            LayoutCommand command,
            CurrentUser operator) {
        List<LayoutItem> normalized = new ArrayList<>(command.beds().size());
        for (LayoutItem item : command.beds()) {
            String requested = item.bedType();
            if (SINGLE_BED.equals(requested)) {
                convertIndependentBed(item.bedId(), roomId, SINGLE_BED);
                normalized.add(copy(item, "LOFT_BED_DESK"));
            } else if ("LOFT_BED_DESK".equals(requested)) {
                convertSingleBedToLoft(item.bedId(), roomId);
                normalized.add(item);
            } else if ("BUNK".equals(requested)) {
                Map<String, Object> bed = lockBed(item.bedId(), roomId);
                String currentType = String.valueOf(bed.get("bed_type"));
                if (!currentType.startsWith("BUNK_")) {
                    requireEmpty(bed);
                }
                normalized.add(item);
            } else {
                normalized.add(item);
            }
        }
        return super.updateLayout(roomId, new LayoutCommand(
                command.expectedRoomVersion(), command.reason(), normalized), operator);
    }

    private void convertIndependentBed(long bedId, long roomId, String targetType) {
        Map<String, Object> bed = lockBed(bedId, roomId);
        String currentType = String.valueOf(bed.get("bed_type"));
        if (targetType.equals(currentType)) return;
        if (bed.get("bed_frame_id") != null || currentType.startsWith("BUNK_")) {
            throw new BusinessException(
                    "BUNK_TO_SINGLE_NOT_SUPPORTED",
                    "上下铺不能直接改为单人床，请先按床位管理流程拆分",
                    HttpStatus.CONFLICT);
        }
        requireEmpty(bed);
        jdbc.update("""
                UPDATE bed
                SET bed_type='SINGLE_BED', bed_frame_id=NULL, version=version+1
                WHERE id=:bedId AND room_id=:roomId
                """, Map.of("bedId", bedId, "roomId", roomId));
    }

    private void convertSingleBedToLoft(long bedId, long roomId) {
        Map<String, Object> bed = lockBed(bedId, roomId);
        if (!SINGLE_BED.equals(String.valueOf(bed.get("bed_type")))) return;
        requireEmpty(bed);
        jdbc.update("""
                UPDATE bed
                SET bed_type='LOFT_BED_DESK', version=version+1
                WHERE id=:bedId AND room_id=:roomId
                """, Map.of("bedId", bedId, "roomId", roomId));
    }

    private Map<String, Object> lockBed(long bedId, long roomId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT bed.id, bed.bed_type, bed.bed_frame_id,
                       CASE WHEN EXISTS (
                           SELECT 1 FROM bed_assignment assignment
                           WHERE assignment.bed_id=bed.id
                       ) OR EXISTS (
                           SELECT 1 FROM room_assignment residency
                           WHERE residency.bed_id=bed.id
                             AND residency.assignment_status='ACTIVE'
                       ) THEN 1 ELSE 0 END AS occupied
                FROM bed
                WHERE bed.id=:bedId AND bed.room_id=:roomId
                FOR UPDATE
                """, Map.of("bedId", bedId, "roomId", roomId));
        if (rows.isEmpty()) {
            throw new BusinessException("BED_NOT_FOUND", "床位不存在或不属于当前房间", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private void requireEmpty(Map<String, Object> bed) {
        if (((Number) bed.get("occupied")).intValue() != 0) {
            throw new BusinessException("BED_TYPE_OCCUPIED", "非空床位不能修改床位类型", HttpStatus.CONFLICT);
        }
    }

    private LayoutItem copy(LayoutItem source, String bedType) {
        return new LayoutItem(source.bedId(), bedType, source.layoutX(), source.layoutZ(), source.rotationDegrees());
    }
}
