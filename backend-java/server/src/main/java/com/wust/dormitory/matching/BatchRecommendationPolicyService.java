package com.wust.dormitory.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class BatchRecommendationPolicyService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BatchRecommendationPolicyService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Policy forBatch(long batchId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT scheme.allowed_recommendation_strategies_json,
                       scheme.default_recommendation_strategy,
                       scheme.weighted_random_base_weight,
                       scheme.weighted_random_temperature
                FROM selection_batch batch
                JOIN matching_weight_scheme scheme
                  ON scheme.id=batch.matching_weight_scheme_id
                WHERE batch.id=:batchId
                """, Map.of("batchId", batchId));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "BATCH_RECOMMENDATION_POLICY_NOT_FOUND",
                    "当前选寝活动没有可用的推荐策略配置",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> row = rows.getFirst();
        List<RecommendationStrategy> allowed = readStrategies(
                row.get("allowed_recommendation_strategies_json"));
        RecommendationStrategy defaultStrategy;
        try {
            defaultStrategy = RecommendationStrategy.valueOf(
                    String.valueOf(row.get("default_recommendation_strategy")));
        } catch (IllegalArgumentException exception) {
            throw invalidPolicy();
        }
        return new Policy(
                allowed,
                defaultStrategy,
                number(row.get("weighted_random_base_weight"), 0.05d),
                number(row.get("weighted_random_temperature"), 0.20d));
    }

    private List<RecommendationStrategy> readStrategies(Object value) {
        try {
            String json = value instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(value);
            List<String> values = objectMapper.readValue(json, STRING_LIST);
            return values.stream().map(RecommendationStrategy::valueOf).toList();
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw invalidPolicy();
        }
    }

    private double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private BusinessException invalidPolicy() {
        return new BusinessException(
                "BATCH_RECOMMENDATION_POLICY_INVALID",
                "当前选寝活动的推荐策略配置无效，请联系学校管理员",
                HttpStatus.CONFLICT);
    }

    public record Policy(
            List<RecommendationStrategy> allowedStrategies,
            RecommendationStrategy defaultStrategy,
            double baseWeight,
            double temperature) {
        public Policy {
            if (allowedStrategies == null || allowedStrategies.isEmpty()) {
                throw new IllegalArgumentException("at least one recommendation strategy is required");
            }
            List<RecommendationStrategy> unique = List.copyOf(new LinkedHashSet<>(allowedStrategies));
            if (!unique.contains(defaultStrategy)) {
                throw new IllegalArgumentException("default strategy must be allowed");
            }
            if (!Double.isFinite(baseWeight) || baseWeight <= 0.0d) {
                throw new IllegalArgumentException("base weight must be finite and greater than zero");
            }
            if (!Double.isFinite(temperature) || temperature <= 0.0d) {
                throw new IllegalArgumentException("temperature must be finite and greater than zero");
            }
            allowedStrategies = unique;
        }

        public void requireAllowed(RecommendationStrategy strategy) {
            if (!allowedStrategies.contains(strategy)) {
                throw new BusinessException(
                        "RECOMMENDATION_STRATEGY_NOT_ALLOWED",
                        "当前选寝活动不允许使用该推荐方式",
                        HttpStatus.FORBIDDEN);
            }
        }

        public List<String> allowedNames() {
            return allowedStrategies.stream().map(Enum::name).toList();
        }
    }
}
