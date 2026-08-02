package com.wust.dormitory.subscription;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class QuotaService {
    private final NamedParameterJdbcTemplate jdbc;
    private final SubscriptionService subscriptionService;
    private final QuotaUsageRepository usageRepository;

    public QuotaService(NamedParameterJdbcTemplate jdbc,
                        SubscriptionService subscriptionService,
                        QuotaUsageRepository usageRepository) {
        this.jdbc = jdbc;
        this.subscriptionService = subscriptionService;
        this.usageRepository = usageRepository;
    }

    public Map<String, Long> currentQuotas() {
        SubscriptionService.CurrentSubscription subscription = subscriptionService.currentSubscription();
        Map<String, Long> quotas = new LinkedHashMap<>(
                subscriptionService.quotasForPlanRevision(subscription.planRevisionId()));
        List<Map<String, Object>> overrides = jdbc.queryForList("""
                SELECT quota_code, quota_value FROM subscription_quota_override
                WHERE subscription_id=:subscriptionId
                  AND effective_from <= :now
                  AND (effective_until IS NULL OR effective_until > :now)
                ORDER BY created_at, id
                """, Map.of("subscriptionId", subscription.subscriptionId(), "now", LocalDateTime.now()));
        for (Map<String, Object> override : overrides) {
            quotas.put(String.valueOf(override.get("quota_code")),
                    ((Number) override.get("quota_value")).longValue());
        }
        return Map.copyOf(quotas);
    }

    @Transactional
    public void requireAvailable(String quotaCode, long increment) {
        if (increment < 0) {
            throw new BusinessException("QUOTA_INCREMENT_INVALID", "配额增量不能为负数");
        }
        Long limit = currentQuotas().get(quotaCode);
        if (limit == null) {
            throw new BusinessException("QUOTA_NOT_CONFIGURED", "当前服务未配置该资源配额：" + quotaCode);
        }
        long used = usageRepository.usage(quotaCode);
        updateAlert(quotaCode, used, limit);
        if (used + increment > limit) {
            throw new BusinessException(
                    "SERVICE_QUOTA_EXCEEDED",
                    "当前资源数量已达到服务容量上限（" + quotaCode
                            + "，上限=" + limit + "，已使用=" + used + "，本次新增=" + increment + "）",
                    HttpStatus.CONFLICT
            );
        }
    }

    public List<QuotaUsage> usageSummary() {
        return currentQuotas().entrySet().stream().map(entry -> {
            long used;
            try {
                used = usageRepository.usage(entry.getKey());
            } catch (BusinessException unsupported) {
                used = 0L;
            }
            long limit = entry.getValue();
            double ratio = limit == 0 ? (used == 0 ? 0D : 1D) : (double) used / (double) limit;
            return new QuotaUsage(entry.getKey(), used, limit, ratio,
                    ratio >= 1D ? "EXCEEDED_100" : ratio >= 0.8D ? "WARNING_80" : "NORMAL");
        }).sorted(java.util.Comparator.comparing(QuotaUsage::quotaCode)).toList();
    }

    private void updateAlert(String quotaCode, long used, long limit) {
        double ratio = limit == 0 ? (used == 0 ? 0D : 1D) : (double) used / (double) limit;
        String level = ratio >= 1D ? "EXCEEDED_100" : ratio >= 0.8D ? "WARNING_80" : null;
        if (level == null) {
            jdbc.update("""
                    UPDATE service_quota_alert SET recovered_at=CURRENT_TIMESTAMP(3)
                    WHERE quota_code=:quotaCode AND recovered_at IS NULL
                    """, Map.of("quotaCode", quotaCode));
            return;
        }
        jdbc.update("""
                INSERT INTO service_quota_alert
                (quota_code, alert_level, used_value, limit_value,
                 first_occurred_at, last_occurred_at, recovered_at)
                VALUES (:quotaCode, :level, :used, :limit,
                        CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3), NULL)
                ON DUPLICATE KEY UPDATE
                    used_value=VALUES(used_value), limit_value=VALUES(limit_value),
                    last_occurred_at=CURRENT_TIMESTAMP(3)
                """, new MapSqlParameterSource()
                .addValue("quotaCode", quotaCode)
                .addValue("level", level)
                .addValue("used", used)
                .addValue("limit", limit));
    }

    public record QuotaUsage(String quotaCode, long used, long limit, double ratio, String status) {
    }
}
