package com.wust.dormitory.platform;

import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.subscription.PlatformAuditService;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PlatformRedisService {
    public static final String CONFIRMATION_TEXT = "CLEAR_REDIS";

    private final StringRedisTemplate redisTemplate;
    private final PlatformAuditService auditService;

    public PlatformRedisService(
            StringRedisTemplate redisTemplate,
            PlatformAuditService auditService) {
        this.redisTemplate = redisTemplate;
        this.auditService = auditService;
    }

    public Map<String, Object> clear(
            String confirmation,
            String reason,
            CurrentUser operator) {
        if (!CONFIRMATION_TEXT.equals(confirmation)) {
            throw new BusinessException(
                    "REDIS_CLEAR_CONFIRMATION_INVALID",
                    "请输入 CLEAR_REDIS 确认清空当前Redis数据库",
                    HttpStatus.BAD_REQUEST);
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new BusinessException(
                    "REDIS_CLEAR_REASON_REQUIRED",
                    "请填写清空Redis的操作原因");
        }

        Map<String, Object> result = redisTemplate.execute(this::clearSelectedDatabase);
        if (result == null) {
            throw new BusinessException(
                    "REDIS_CLEAR_FAILED",
                    "Redis未返回清理结果",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
        auditService.success(
                "REDIS_DATABASE_CLEAR",
                operator.userId(),
                "REDIS_DATABASE",
                "CURRENT",
                normalizedReason,
                Map.of("keyCount", result.get("beforeKeyCount")),
                result);
        return result;
    }

    private Map<String, Object> clearSelectedDatabase(RedisConnection connection) {
        Long before = connection.serverCommands().dbSize();
        connection.serverCommands().flushDb();
        Long after = connection.serverCommands().dbSize();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cleared", true);
        result.put("beforeKeyCount", before == null ? 0L : before);
        result.put("afterKeyCount", after == null ? 0L : after);
        result.put("scope", "CURRENT_DATABASE");
        return result;
    }
}
