package com.wust.dormitory.residency;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.mapper.RoomOccupancySnapshotMapper;
import com.wust.dormitory.residency.model.persistence.RoomOccupancySnapshotRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BatchRoomLockService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyPolicyService policy;
    private final RoomOccupancySnapshotMapper roomOccupancySnapshotMapper;

    public BatchRoomLockService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyPolicyService policy,
            RoomOccupancySnapshotMapper roomOccupancySnapshotMapper) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.roomOccupancySnapshotMapper = roomOccupancySnapshotMapper;
    }

    public Map<String, Object> preview(long batchId) {
        Map<String, Object> batch = policy.batch(batchId);
        Set<Long> roomIds = policy.roomIdsForBatch(batchId);
        List<RoomOccupancySnapshotRow> snapshots = roomIds.isEmpty()
                ? List.of()
                : roomOccupancySnapshotMapper.findSnapshots(batchId, new ArrayList<>(roomIds));
        List<Map<String, Object>> rooms = new ArrayList<>();
        List<Map<String, Object>> blockers = new ArrayList<>();
        int totalCapacity = 0;
        int availableCapacity = 0;

        for (RoomOccupancySnapshotRow snapshot : snapshots) {
            Map<String, Object> room = snapshot.roomMap();
            int activeResidents = snapshot.activeResidents();
            int unknownBeds = snapshot.unknownBeds();
            int remaining = snapshot.remainingCapacity();
            totalCapacity += snapshot.capacity();
            availableCapacity += remaining;

            List<Map<String, Object>> roomIssues = new ArrayList<>();
            if (!"ENABLED".equals(snapshot.operationalStatus())) {
                roomIssues.add(issue("ROOM_NOT_AVAILABLE", "寝室当前不可用"));
            }
            if (((Number) batch.get("separate_student_categories")).intValue() == 1
                    && "MIXED".equals(snapshot.residentScope())) {
                roomIssues.add(issue(
                        "MIXED_ROOM_NOT_ALLOWED",
                        "当前批次要求国内生和国际生分开选寝，混住宿舍不能加入批次"));
            }
            if ("BED".equals(String.valueOf(batch.get("selection_mode"))) && unknownBeds > 0) {
                roomIssues.add(issue(
                        "ROOM_BED_MAPPING_REQUIRED",
                        unknownBeds + "名在住学生尚未确认实际床位，不能开放选床模式"));
            }
            if (snapshot.conflictBatchName() != null) {
                roomIssues.add(issue(
                        "ROOM_ACTIVE_BATCH_CONFLICT",
                        "该寝室正在被批次“" + snapshot.conflictBatchName() + "”使用"));
            }

            Map<String, Object> view = new LinkedHashMap<>(room);
            view.put("activeResidents", activeResidents);
            view.put("confirmedBeds", activeResidents - unknownBeds);
            view.put("unconfirmedBeds", unknownBeds);
            view.put("remainingCapacity", remaining);
            view.put("bedModeAvailable", unknownBeds == 0);
            view.put("issues", roomIssues);
            rooms.add(view);
            if (!roomIssues.isEmpty()) {
                blockers.add(view);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("roomCount", rooms.size());
        result.put("totalCapacity", totalCapacity);
        result.put("availableCapacity", availableCapacity);
        result.put("publishable", !rooms.isEmpty() && blockers.isEmpty());
        result.put("rooms", rooms);
        result.put("blockers", blockers);
        return result;
    }

    public void requirePublishable(long batchId) {
        Map<String, Object> preview = preview(batchId);
        if (((Number) preview.get("roomCount")).intValue() == 0) {
            throw new BusinessException(
                    "BATCH_ROOM_SCOPE_REQUIRED",
                    "批次没有配置任何可选寝室",
                    HttpStatus.CONFLICT);
        }
        if (!Boolean.TRUE.equals(preview.get("publishable"))) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) preview.get("blockers");
            String firstMessage = blockers.isEmpty()
                    ? "批次房间预检未通过"
                    : blockerMessage(blockers.getFirst());
            throw new BusinessException(
                    "BATCH_ROOM_PREFLIGHT_FAILED",
                    firstMessage,
                    HttpStatus.CONFLICT);
        }
    }

    public void acquire(long batchId) {
        Map<String, Object> batch = policy.batch(batchId);
        Set<Long> roomIds = policy.roomIdsForBatch(batchId);
        if (roomIds.isEmpty()) {
            throw new BusinessException("BATCH_ROOM_SCOPE_REQUIRED", "批次没有配置任何可选寝室");
        }
        try {
            for (Long roomId : roomIds) {
                jdbc.update("""
                        INSERT INTO active_batch_room_lock
                        (room_id, batch_id, selection_mode)
                        VALUES (:roomId, :batchId, :selectionMode)
                        """, new MapSqlParameterSource()
                        .addValue("roomId", roomId)
                        .addValue("batchId", batchId)
                        .addValue("selectionMode", batch.get("selection_mode")));
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "ROOM_ACTIVE_BATCH_CONFLICT",
                    "至少一个寝室已经属于其他活动批次，请重新检查批次范围",
                    HttpStatus.CONFLICT);
        }
    }

    public void release(long batchId) {
        jdbc.update(
                "DELETE FROM active_batch_room_lock WHERE batch_id=:batchId",
                Map.of("batchId", batchId));
    }

    private Map<String, Object> issue(String code, String message) {
        return Map.of("code", code, "message", message);
    }

    private String blockerMessage(Map<String, Object> room) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issues = (List<Map<String, Object>>) room.get("issues");
        String roomLabel = room.get("building_name") + " " + room.get("room_number");
        return issues.isEmpty()
                ? roomLabel + "未通过发布预检"
                : roomLabel + "：" + issues.getFirst().get("message");
    }
}
