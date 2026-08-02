package com.wust.dormitory.residency;

import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BatchCapacityService {
    private static final Set<String> ENROLLABLE_STATUSES = Set.of("DRAFT", "PUBLISHED", "OPEN", "PAUSED");
    private static final Set<String> ACTIVE_STATUSES = Set.of("PUBLISHED", "OPEN", "PAUSED");

    private final NamedParameterJdbcTemplate jdbc;
    private final ResidencyPolicyService policy;
    private final AuditService auditService;

    public BatchCapacityService(
            NamedParameterJdbcTemplate jdbc,
            ResidencyPolicyService policy,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.policy = policy;
        this.auditService = auditService;
    }

    public Map<String, Object> preview(long batchId, long studentId) {
        Map<String, Object> batch = policy.batch(batchId);
        Map<String, Object> student = policy.student(studentId);
        String status = String.valueOf(batch.get("batch_status"));
        List<Map<String, Object>> eligibleRooms = new ArrayList<>();
        List<Map<String, Object>> rejectedRooms = new ArrayList<>();
        int totalSlots = 0;

        for (Long roomId : policy.roomIdsForBatch(batchId)) {
            Map<String, Object> room = policy.room(roomId, false);
            List<Map<String, String>> reasons = new ArrayList<>();
            try {
                policy.requireStudentEligibleForRoom(student, batch, room);
            } catch (BusinessException exception) {
                reasons.add(Map.of("code", exception.getCode(), "message", exception.getMessage()));
            }
            int slots;
            if ("BED".equals(String.valueOf(batch.get("selection_mode")))) {
                int unknown = policy.unknownBedResidentCount(roomId);
                if (unknown > 0) {
                    reasons.add(Map.of(
                            "code", "ROOM_BED_MAPPING_REQUIRED",
                            "message", unknown + "名在住学生尚未确认实际床位"));
                }
                slots = unknown == 0 ? policy.availableBedCount(roomId) : 0;
            } else {
                slots = policy.availableCapacity(roomId);
            }
            if (slots <= 0) {
                reasons.add(Map.of("code", "ROOM_CAPACITY_FULL", "message", "寝室已无剩余名额"));
            }
            Map<String, Object> view = new LinkedHashMap<>();
            view.putAll(room);
            view.put("availableSlots", slots);
            view.put("reasons", reasons);
            if (reasons.isEmpty()) {
                eligibleRooms.add(view);
                totalSlots += slots;
            } else {
                rejectedRooms.add(view);
            }
        }

        boolean existingEligibility = count("""
                SELECT COUNT(*) FROM batch_student_eligibility
                WHERE batch_id=:batchId AND student_id=:studentId
                """, batchId, studentId) > 0;
        boolean activeResidency = count("""
                SELECT COUNT(*) FROM room_assignment
                WHERE student_id=:studentId AND assignment_status='ACTIVE'
                """, batchId, studentId) > 0;
        boolean activeBatchConflict = count("""
                SELECT COUNT(*) FROM active_batch_student_lock
                WHERE student_id=:studentId AND batch_id<>:batchId
                """, batchId, studentId) > 0;

        List<Map<String, String>> blockers = new ArrayList<>();
        if (!ENROLLABLE_STATUSES.contains(status)) {
            blockers.add(Map.of("code", "BATCH_NOT_ENROLLABLE", "message", "当前批次状态不能新增学生"));
        }
        if (activeResidency) {
            blockers.add(Map.of("code", "STUDENT_ALREADY_RESIDENT", "message", "学生已存在有效寝室归属"));
        }
        if (activeBatchConflict) {
            blockers.add(Map.of("code", "BATCH_STUDENT_ACTIVE_CONFLICT", "message", "学生正在参加另一个活动批次"));
        }
        if (totalSlots <= 0) {
            blockers.add(Map.of("code", "NO_ELIGIBLE_ROOM_CAPACITY", "message", "没有符合性别和学生类别的剩余宿舍名额"));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("batch", batch);
        result.put("student", student);
        result.put("existingEligibility", existingEligibility);
        result.put("eligible", blockers.isEmpty());
        result.put("eligibleRoomCount", eligibleRooms.size());
        result.put("availableSlots", totalSlots);
        result.put("eligibleRooms", eligibleRooms);
        result.put("rejectedRooms", rejectedRooms);
        result.put("blockers", blockers);
        return result;
    }

    @Transactional
    public Map<String, Object> enroll(
            long batchId,
            long studentId,
            String sourceType,
            String reason,
            CurrentUser operator) {
        Map<String, Object> preview = preview(batchId, studentId);
        if (!Boolean.TRUE.equals(preview.get("eligible"))) {
            throw new BusinessException(
                    "STUDENT_BATCH_CAPACITY_CHECK_FAILED",
                    firstBlocker(preview),
                    HttpStatus.CONFLICT);
        }
        String status = String.valueOf(((Map<?, ?>) preview.get("batch")).get("batch_status"));
        jdbc.update("""
                INSERT INTO batch_student_eligibility
                (batch_id, student_id, eligibility_status, reason_code,
                 source_type, added_by, added_at)
                VALUES (:batchId, :studentId, 'ELIGIBLE', 'MANUAL_ADD',
                        :sourceType, :operatorId, CURRENT_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE
                    eligibility_status='ELIGIBLE',
                    reason_code='MANUAL_ADD',
                    source_type=VALUES(source_type),
                    added_by=VALUES(added_by),
                    added_at=VALUES(added_at)
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId)
                .addValue("sourceType", normalizeSource(sourceType))
                .addValue("operatorId", operator.userId()));
        if (ACTIVE_STATUSES.contains(status)) {
            try {
                jdbc.update("""
                        INSERT INTO active_batch_student_lock (student_id, batch_id)
                        VALUES (:studentId, :batchId)
                        """, new MapSqlParameterSource()
                        .addValue("studentId", studentId)
                        .addValue("batchId", batchId));
            } catch (DuplicateKeyException exception) {
                throw new BusinessException(
                        "BATCH_STUDENT_ACTIVE_CONFLICT",
                        "学生正在参加另一个活动批次",
                        HttpStatus.CONFLICT);
            }
        }
        auditService.success(
                operator,
                "BATCH_STUDENT_MANUAL_ENROLL",
                "SELECTION_BATCH",
                batchId,
                requiredReason(reason),
                null,
                Map.of(
                        "studentId", studentId,
                        "sourceType", normalizeSource(sourceType),
                        "availableSlotsAtEnrollment", preview.get("availableSlots")));
        return preview(batchId, studentId);
    }

    private int count(String sql, long batchId, long studentId) {
        Integer value = jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("studentId", studentId), Integer.class);
        return value == null ? 0 : value;
    }

    private String firstBlocker(Map<String, Object> preview) {
        @SuppressWarnings("unchecked")
        List<Map<String, String>> blockers = (List<Map<String, String>>) preview.get("blockers");
        return blockers.isEmpty() ? "学生不满足加入当前批次的条件" : blockers.getFirst().get("message");
    }

    private String normalizeSource(String sourceType) {
        return "TRANSFER_MANUAL".equals(sourceType) ? "TRANSFER_MANUAL" : "ADMIN_MANUAL";
    }

    private String requiredReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("CHANGE_REASON_REQUIRED", "必须填写操作原因");
        }
        return reason.trim();
    }
}
