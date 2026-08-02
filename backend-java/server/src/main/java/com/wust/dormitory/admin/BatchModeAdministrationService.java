package com.wust.dormitory.admin;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class BatchModeAdministrationService {
    private final NamedParameterJdbcTemplate jdbc;

    public BatchModeAdministrationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> batches() {
        return jdbc.queryForList("""
                SELECT sb.*,
                       (SELECT COUNT(*) FROM batch_student_eligibility e
                        WHERE e.batch_id=sb.id AND e.eligibility_status='ELIGIBLE') AS eligible_count,
                       (SELECT COUNT(*) FROM bed_assignment a
                        WHERE a.batch_id=sb.id AND a.assignment_status='ACTIVE') AS bed_assigned_count,
                       (SELECT COUNT(*) FROM room_assignment ra
                        WHERE ra.batch_id=sb.id AND ra.assignment_status='ACTIVE') AS room_assigned_count,
                       (SELECT COUNT(*) FROM active_batch_room_lock l
                        WHERE l.batch_id=sb.id) AS locked_room_count,
                       (SELECT COUNT(*) FROM room_assignment ra
                        JOIN batch_room_scope brs ON brs.room_id=ra.room_id
                        WHERE brs.batch_id=sb.id AND ra.assignment_status='ACTIVE'
                          AND ra.bed_id IS NULL) AS unconfirmed_bed_resident_count
                FROM selection_batch sb
                ORDER BY sb.created_at DESC
                """, Map.of());
    }

    public void copyModeSettings(long sourceBatchId, long targetBatchId) {
        int updated = jdbc.update("""
                UPDATE selection_batch target
                JOIN selection_batch source ON source.id=:sourceBatchId
                SET target.selection_mode=source.selection_mode,
                    target.separate_student_categories=source.separate_student_categories
                WHERE target.id=:targetBatchId
                  AND target.batch_status='DRAFT'
                """, Map.of(
                "sourceBatchId", sourceBatchId,
                "targetBatchId", targetBatchId));
        if (updated != 1) {
            throw new BusinessException(
                    "BATCH_COPY_MODE_SYNC_FAILED",
                    "批次复制后同步选择模式失败",
                    HttpStatus.CONFLICT);
        }
    }
}
