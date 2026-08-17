package com.wust.dormitory.selection;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.subscription.AccessMode;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class BatchSelectionModeGuard {
    public static final String ROOM = "ROOM";
    public static final String BED = "BED";

    private final NamedParameterJdbcTemplate jdbc;
    private final FeatureAccessService featureAccessService;

    public BatchSelectionModeGuard(
            NamedParameterJdbcTemplate jdbc,
            FeatureAccessService featureAccessService) {
        this.jdbc = jdbc;
        this.featureAccessService = featureAccessService;
    }

    public String normalize(String mode) {
        String normalized = mode == null || mode.isBlank()
                ? BED
                : mode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!ROOM.equals(normalized) && !BED.equals(normalized)) {
            throw new BusinessException(
                    "BATCH_SELECTION_MODE_INVALID",
                    "批次选择模式必须为选择寝室或选择床位");
        }
        return normalized;
    }

    public void requireModeAvailableForNewBatch(String mode) {
        String normalized = normalize(mode);
        if (BED.equals(normalized)) {
            featureAccessService.require(
                    FeatureCodes.P2_BED_SELECTION_MODE,
                    AccessMode.START_NEW,
                    null);
        }
    }

    public String mode(long batchId) {
        List<String> rows = jdbc.queryForList("""
                SELECT selection_mode FROM selection_batch WHERE id=:batchId
                """, Map.of("batchId", batchId), String.class);
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "BATCH_NOT_FOUND",
                    "选寝批次不存在",
                    HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    public void requireRoomMode(long batchId) {
        String mode = mode(batchId);
        if (!ROOM.equals(mode)) {
            throw mismatch("当前批次为选择床位模式，不能直接选择寝室");
        }
    }

    public void requireBedMode(long batchId) {
        String mode = mode(batchId);
        if (!BED.equals(mode)) {
            throw mismatch("当前批次为选择寝室模式，学生不需要选择具体床位");
        }
        featureAccessService.require(
                FeatureCodes.P2_BED_SELECTION_MODE,
                AccessMode.CONTINUE_EXISTING_BATCH,
                batchId);
    }

    public void requireBedModeForPublish(long batchId) {
        String mode = mode(batchId);
        if (BED.equals(mode)) {
            featureAccessService.require(
                    FeatureCodes.P2_BED_SELECTION_MODE,
                    AccessMode.START_NEW,
                    batchId);
        }
    }

    public void requireModeAvailableForExistingBatch(long batchId) {
        String mode = mode(batchId);
        if (BED.equals(mode)) {
            featureAccessService.require(
                    FeatureCodes.P2_BED_SELECTION_MODE,
                    AccessMode.CONTINUE_EXISTING_BATCH,
                    batchId);
        }
    }

    private BusinessException mismatch(String message) {
        return new BusinessException(
                "BATCH_SELECTION_MODE_MISMATCH",
                message,
                HttpStatus.CONFLICT);
    }
}
