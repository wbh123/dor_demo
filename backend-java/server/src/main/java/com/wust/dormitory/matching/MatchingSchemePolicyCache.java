package com.wust.dormitory.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.mapper.MatchingSchemeMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class MatchingSchemePolicyCache {
    private static final String SCHEME_KEY_PREFIX = "dorm:matching:scheme:";
    private static final TypeReference<Map<String, Double>> DOUBLE_MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final MatchingSchemeMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public MatchingSchemePolicyCache(
            MatchingSchemeMapper mapper,
            StringRedisTemplate redis,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public MatchingSchemeService.Policy policyForBatch(long batchId) {
        Long schemeId = mapper.findPolicySchemeIdForBatch(batchId);
        if (schemeId == null) {
            throw new BusinessException(
                    "MATCHING_SCHEME_NOT_FOUND",
                    "当前批次未配置匹配方案",
                    HttpStatus.CONFLICT);
        }
        String key = SCHEME_KEY_PREFIX + schemeId;
        MatchingSchemeService.Policy cached = read(key);
        if (cached != null) return cached;

        Map<String, Object> row = mapper.findPolicyScheme(schemeId);
        if (row == null || row.isEmpty()) {
            throw new BusinessException(
                    "MATCHING_SCHEME_NOT_FOUND",
                    "匹配方案不存在",
                    HttpStatus.NOT_FOUND);
        }
        MatchingSchemeService.Policy policy = policy(row);
        write(key, policy);
        return policy;
    }

    public void invalidate(long schemeId) {
        Runnable action = () -> delete(SCHEME_KEY_PREFIX + schemeId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private MatchingSchemeService.Policy policy(Map<String, Object> row) {
        return new MatchingSchemeService.Policy(
                number(row.get("id")),
                String.valueOf(row.get("scheme_code")),
                String.valueOf(row.get("scheme_name")),
                integer(row.get("revision")),
                String.valueOf(row.get("algorithm_version")),
                readDoubleMap(jsonText(row.get("weights_json"))),
                readDoubleMap(jsonText(row.get("conflict_rules_json"))),
                readStrategies(jsonText(row.get("allowed_recommendation_strategies_json"))),
                RecommendationStrategy.valueOf(String.valueOf(row.get("default_recommendation_strategy"))),
                decimal(row.get("weighted_random_base_weight")),
                decimal(row.get("weighted_random_temperature")));
    }

    private MatchingSchemeService.Policy read(String key) {
        try {
            String payload = redis.opsForValue().get(key);
            return payload == null ? null : objectMapper.readValue(payload, MatchingSchemeService.Policy.class);
        } catch (RuntimeException | JsonProcessingException ignored) {
            return null;
        }
    }

    private void write(String key, MatchingSchemeService.Policy policy) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(policy));
        } catch (RuntimeException | JsonProcessingException ignored) {
            // Redis is an acceleration layer. MySQL remains the source of truth.
        }
    }

    private void delete(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // Cache invalidation is fail-open; immutable scheme rows remain authoritative in MySQL.
        }
    }

    private Map<String, Double> readDoubleMap(String json) {
        try {
            return objectMapper.readValue(json, DOUBLE_MAP);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("MATCHING_SCHEME_DATA_INVALID", "匹配方案数据无法解析");
        }
    }

    private List<RecommendationStrategy> readStrategies(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST).stream()
                    .map(RecommendationStrategy::valueOf)
                    .toList();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new BusinessException("MATCHING_SCHEME_DATA_INVALID", "推荐策略配置无法解析");
        }
    }

    private String jsonText(Object value) {
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return value == null ? "{}" : String.valueOf(value);
    }

    private long number(Object value) { return ((Number) value).longValue(); }
    private int integer(Object value) { return ((Number) value).intValue(); }
    private double decimal(Object value) { return ((Number) value).doubleValue(); }
}
