package com.wust.dormitory.selection;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BedScopeGuard {
    private final NamedParameterJdbcTemplate jdbc;

    public BedScopeGuard(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void requireAllowed(long batchId, long bedId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM bed
                WHERE id=:bedId
                  AND (
                    NOT EXISTS (
                      SELECT 1 FROM batch_bed_scope configured
                      WHERE configured.batch_id=:batchId
                    )
                    OR EXISTS (
                      SELECT 1 FROM batch_bed_scope allowed
                      WHERE allowed.batch_id=:batchId AND allowed.bed_id=:bedId
                    )
                  )
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("bedId", bedId), Integer.class);
        if (count == null || count == 0) {
            throw new BusinessException(
                    "BED_OUT_OF_SCOPE",
                    "床位不在当前批次可选范围",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    public void requireAllowed(long batchId, Collection<Long> bedIds) {
        if (bedIds == null || bedIds.isEmpty()) {
            throw new BusinessException("BED_REQUIRED", "床位列表不能为空");
        }
        for (Long bedId : bedIds) {
            requireAllowed(batchId, bedId);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> filterRoomSnapshot(long batchId, Map<String, Object> snapshot) {
        Object value = snapshot.get("beds");
        if (!(value instanceof List<?> original)) {
            return snapshot;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Object item : original) {
            if (!(item instanceof Map<?, ?> raw) || raw.get("id") == null) {
                continue;
            }
            long bedId = ((Number) raw.get("id")).longValue();
            if (isAllowed(batchId, bedId)) {
                filtered.add((Map<String, Object>) item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>(snapshot);
        result.put("beds", filtered);
        return result;
    }

    private boolean isAllowed(long batchId, long bedId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM bed
                WHERE id=:bedId
                  AND (
                    NOT EXISTS (SELECT 1 FROM batch_bed_scope WHERE batch_id=:batchId)
                    OR EXISTS (
                      SELECT 1 FROM batch_bed_scope
                      WHERE batch_id=:batchId AND bed_id=:bedId
                    )
                  )
                """, new MapSqlParameterSource()
                .addValue("batchId", batchId)
                .addValue("bedId", bedId), Integer.class);
        return count != null && count > 0;
    }
}
