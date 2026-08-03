package com.wust.dormitory.allocation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.realtime.RoomEventHub;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AssignmentAdjustmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final RoomEventHub eventHub;

    public AssignmentAdjustmentService(NamedParameterJdbcTemplate jdbc,
                                       ObjectMapper objectMapper,
                                       AuditService auditService,
                                       RoomEventHub eventHub) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.eventHub = eventHub;
    }

    @Transactional
    public Map<String, Object> adjust(long assignmentId,
                                      long newBedId,
                                      String reason,
                                      CurrentUser operator) {
        Map<String, Object> current = one("""
                SELECT a.id,
                       a.batch_id,
                       a.student_id,
                       a.bed_id AS old_bed_id,
                       old_bed.room_id AS old_room_id,
                       s.gender AS student_gender
                FROM bed_assignment a
                JOIN student s ON s.id=a.student_id
                JOIN bed old_bed ON old_bed.id=a.bed_id
                WHERE a.id=:assignmentId
                FOR UPDATE
                """, Map.of("assignmentId", assignmentId),
                "ASSIGNMENT_NOT_FOUND", "当前分配不存在");

        long batchId = ((Number) current.get("batch_id")).longValue();
        long studentId = ((Number) current.get("student_id")).longValue();
        long oldBedId = ((Number) current.get("old_bed_id")).longValue();
        long oldRoomId = ((Number) current.get("old_room_id")).longValue();
        if (oldBedId == newBedId) {
            throw new BusinessException("BED_NOT_CHANGED", "目标床位与当前床位相同");
        }

        Map<String, Object> target = one("""
                SELECT bed.id AS bed_id,
                       bed.room_id,
                       bed.operational_status AS bed_status,
                       r.gender_restriction,
                       r.operational_status AS room_status,
                       EXISTS(
                         SELECT 1 FROM bed_assignment occupied
                         WHERE occupied.batch_id=:batchId
                           AND occupied.bed_id=bed.id
                       ) AS occupied
                FROM bed
                JOIN room r ON r.id=bed.room_id
                JOIN dormitory_floor f ON f.id=r.floor_id
                WHERE bed.id=:newBedId
                  AND (
                    EXISTS(
                      SELECT 1 FROM batch_room_scope rs
                      WHERE rs.batch_id=:batchId AND rs.room_id=r.id
                    )
                    OR EXISTS(
                      SELECT 1 FROM batch_building_scope bs
                      WHERE bs.batch_id=:batchId AND bs.building_id=f.building_id
                    )
                  )
                  AND (
                    NOT EXISTS(
                      SELECT 1 FROM batch_bed_scope configured
                      WHERE configured.batch_id=:batchId
                    )
                    OR EXISTS(
                      SELECT 1 FROM batch_bed_scope allowed
                      WHERE allowed.batch_id=:batchId AND allowed.bed_id=bed.id
                    )
                  )
                FOR UPDATE
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("newBedId", newBedId),
                "TARGET_BED_NOT_FOUND", "目标床位不存在或不在批次范围");

        if (!"ENABLED".equals(String.valueOf(target.get("bed_status")))
                || !"ENABLED".equals(String.valueOf(target.get("room_status")))) {
            throw new BusinessException("TARGET_BED_DISABLED", "目标房间或床位不可用");
        }
        if (((Number) target.get("occupied")).intValue() == 1) {
            throw new BusinessException(
                    "TARGET_BED_OCCUPIED",
                    "目标床位已经分配",
                    HttpStatus.CONFLICT
            );
        }
        if (!current.get("student_gender").equals(target.get("gender_restriction"))) {
            throw new BusinessException(
                    "ROOM_GENDER_MISMATCH",
                    "学生性别与目标房间性别不一致"
            );
        }

        long newRoomId = ((Number) target.get("room_id")).longValue();
        jdbc.update("""
                UPDATE bed_assignment
                SET bed_id=:newBedId,
                    assignment_method='MANUAL_ADJUSTMENT',
                    assigned_by=:operatorId,
                    assigned_at=CURRENT_TIMESTAMP(3),
                    version=version+1
                WHERE id=:assignmentId
                """, new MapSqlParameterSource()
                .addValue("newBedId", newBedId)
                .addValue("operatorId", operator.userId())
                .addValue("assignmentId", assignmentId));

        Map<String, Object> previousData = new LinkedHashMap<>();
        previousData.put("bedId", oldBedId);
        previousData.put("roomId", oldRoomId);
        Map<String, Object> currentData = new LinkedHashMap<>();
        currentData.put("bedId", newBedId);
        currentData.put("roomId", newRoomId);

        jdbc.update("""
                INSERT INTO assignment_history
                (assignment_id, batch_id, student_id, bed_id,
                 event_type, assignment_method, operator_user_id,
                 reason, previous_data, current_data, occurred_at)
                VALUES
                (:assignmentId, :batchId, :studentId, :newBedId,
                 'ADJUSTED', 'MANUAL_ADJUSTMENT', :operatorId,
                 :reason, CAST(:previousData AS JSON),
                 CAST(:currentData AS JSON), CURRENT_TIMESTAMP(3))
                """, new MapSqlParameterSource()
                .addValue("assignmentId", assignmentId)
                .addValue("batchId", batchId)
                .addValue("studentId", studentId)
                .addValue("newBedId", newBedId)
                .addValue("operatorId", operator.userId())
                .addValue("reason", reason)
                .addValue("previousData", json(previousData))
                .addValue("currentData", json(currentData)));

        auditService.success(
                operator,
                "ASSIGNMENT_ADJUST",
                "BED_ASSIGNMENT",
                assignmentId,
                reason,
                previousData,
                currentData
        );

        afterCommit(() -> {
            eventHub.publish(batchId, oldRoomId, "BED_RELEASED", Map.of("bedId", oldBedId));
            eventHub.publish(batchId, newRoomId, "BED_ASSIGNED", Map.of("bedId", newBedId));
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assignmentId", assignmentId);
        result.put("studentId", studentId);
        result.put("oldBedId", oldBedId);
        result.put("newBedId", newBedId);
        result.put("reason", reason);
        return result;
    }

    private Map<String, Object> one(String sql,
                                    Map<String, ?> parameters,
                                    String code,
                                    String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private Map<String, Object> one(String sql,
                                    MapSqlParameterSource parameters,
                                    String code,
                                    String message) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, parameters);
        if (rows.isEmpty()) {
            throw new BusinessException(code, message, HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    "JSON_ERROR",
                    "分配调整历史序列化失败",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private void afterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            runnable.run();
                        }
                    }
            );
        } else {
            runnable.run();
        }
    }
}
