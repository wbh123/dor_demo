package com.wust.dormitory.residency;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SelectionModeGuard {
    private final NamedParameterJdbcTemplate jdbc;

    public SelectionModeGuard(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String mode(long batchId) {
        List<String> rows = jdbc.query(
                "SELECT selection_mode FROM selection_batch WHERE id=:batchId",
                Map.of("batchId", batchId),
                (rs, rowNum) -> rs.getString(1));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "BATCH_NOT_FOUND",
                    "选寝批次不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    public void requireRoomMode(long batchId) {
        require(batchId, "ROOM", "当前批次为选择床位模式，不能使用选择寝室操作");
    }

    public void requireBedMode(long batchId) {
        require(batchId, "BED", "当前批次为选择寝室模式，不能选择或占用具体床位");
    }

    private void require(long batchId, String expected, String message) {
        if (!expected.equals(mode(batchId))) {
            throw new BusinessException(
                    "BATCH_SELECTION_MODE_MISMATCH",
                    message,
                    HttpStatus.CONFLICT);
        }
    }
}
