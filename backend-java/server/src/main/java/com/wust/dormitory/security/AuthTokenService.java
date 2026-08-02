package com.wust.dormitory.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthTokenService {
    private static final String PREFIX = "dormitory:auth:token:";
    private static final Duration TOKEN_TTL = Duration.ofHours(8);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AuthTokenService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Token issue(CurrentUser user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        try {
            redisTemplate.opsForValue().set(PREFIX + token, objectMapper.writeValueAsString(user), TOKEN_TTL);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("TOKEN_CREATE_FAILED", "登录令牌创建失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new Token(token, TOKEN_TTL.toSeconds());
    }

    public Optional<CurrentUser> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().get(PREFIX + token);
        if (value == null) {
            return Optional.empty();
        }
        try {
            CurrentUser user = objectMapper.readValue(value, CurrentUser.class);
            redisTemplate.expire(PREFIX + token, TOKEN_TTL);
            return Optional.of(user);
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(PREFIX + token);
            return Optional.empty();
        }
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(PREFIX + token);
        }
    }

    public int revokeUser(long userId) {
        Set<String> keys = redisTemplate.keys(PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        List<String> matched = new ArrayList<>();
        for (String key : keys) {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                continue;
            }
            try {
                CurrentUser currentUser = objectMapper.readValue(value, CurrentUser.class);
                if (currentUser.userId() == userId) {
                    matched.add(key);
                }
            } catch (JsonProcessingException exception) {
                matched.add(key);
            }
        }
        if (!matched.isEmpty()) {
            redisTemplate.delete(matched);
        }
        return matched.size();
    }

    public record Token(String accessToken, long expiresInSeconds) {
    }
}
