package com.wust.dormitory.selection;

import com.wust.dormitory.common.error.BusinessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BedHoldResetService {
    private static final String HOLD_PATTERN = "dormitory:batch:*:bed:*:hold";

    private final StringRedisTemplate redis;

    public BedHoldResetService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public int releaseAllForStudent(long studentId, List<Long> teamIds) {
        try {
            Set<String> keys = redis.keys(HOLD_PATTERN);
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            Set<Long> teams = new HashSet<>(teamIds == null ? List.of() : teamIds);
            Set<String> matched = new HashSet<>();
            for (String key : keys) {
                String value = redis.opsForValue().get(key);
                if (value == null) {
                    continue;
                }
                if (value.startsWith("S:" + studentId + ":") || heldByTeam(value, teams)) {
                    matched.add(key);
                }
            }
            if (matched.isEmpty()) {
                return 0;
            }
            Long deleted = redis.delete((Collection<String>) matched);
            return deleted == null ? 0 : deleted.intValue();
        } catch (RedisConnectionFailureException exception) {
            throw new BusinessException(
                    "REDIS_UNAVAILABLE",
                    "临时占用服务不可用，学生状态未重置，请稍后重试",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private boolean heldByTeam(String value, Set<Long> teamIds) {
        if (!value.startsWith("T:")) {
            return false;
        }
        int separator = value.indexOf(':', 2);
        if (separator < 0) {
            return false;
        }
        try {
            return teamIds.contains(Long.parseLong(value.substring(2, separator)));
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}
