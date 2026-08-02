package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Year;
import java.util.Map;

@Repository
public class QuotaUsageRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public QuotaUsageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long usage(String quotaCode) {
        String sql = switch (quotaCode) {
            case QuotaCodes.MAX_ADMIN_USERS -> "SELECT COUNT(*) FROM app_user WHERE user_type='ADMIN' AND account_status='ACTIVE'";
            case QuotaCodes.MAX_STUDENTS -> "SELECT COUNT(*) FROM student";
            case QuotaCodes.MAX_CAMPUSES -> "SELECT COUNT(*) FROM campus";
            case QuotaCodes.MAX_BUILDINGS -> "SELECT COUNT(*) FROM dormitory_building";
            case QuotaCodes.MAX_ROOMS -> "SELECT COUNT(*) FROM room";
            case QuotaCodes.MAX_BEDS -> "SELECT COUNT(*) FROM bed";
            case QuotaCodes.MAX_BATCHES_PER_YEAR -> """
                    SELECT COUNT(*) FROM selection_batch
                    WHERE created_at >= :yearStart AND created_at < :nextYearStart
                    """;
            case QuotaCodes.MAX_CONCURRENT_ACTIVE_BATCHES -> """
                    SELECT COUNT(*) FROM selection_batch
                    WHERE batch_status IN ('PUBLISHED','OPEN','PAUSED')
                    """;
            default -> throw new BusinessException("QUOTA_USAGE_UNSUPPORTED", "当前配额尚未接入业务计量：" + quotaCode);
        };
        Map<String, Object> params = Map.of(
                "yearStart", Year.now().atDay(1).atStartOfDay(),
                "nextYearStart", Year.now().plusYears(1).atDay(1).atStartOfDay()
        );
        Long result = jdbc.queryForObject(sql, params, Long.class);
        return result == null ? 0L : result;
    }
}
