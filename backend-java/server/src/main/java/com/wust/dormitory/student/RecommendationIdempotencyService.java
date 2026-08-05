package com.wust.dormitory.student;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.RecommendationStrategy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class RecommendationIdempotencyService {
    private static final Pattern CLIENT_REQUEST_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public RecommendationIdempotencyService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> persistOrGet(
            long studentId,
            long batchId,
            String clientRequestId,
            RecommendationStrategy strategy,
            Map<String, Object> generated) {
        String requestId = normalizeClientRequestId(clientRequestId);
        String candidateVersion = requiredValue(generated, "candidateVersion");
        List<Map<String, Object>> existing = find(
                studentId,
                batchId,
                requestId,
                candidateVersion,
                true);
        if (!existing.isEmpty()) {
            return existingResponse(existing.getFirst(), strategy);
        }

        try {
            jdbc.update("""
                    INSERT INTO student_recommendation_request
                    (student_id, batch_id, client_request_id, strategy,
                     candidate_version, algorithm_version, seed_digest,
                     response_json, created_at, updated_at)
                    VALUES
                    (:studentId, :batchId, :clientRequestId, :strategy,
                     :candidateVersion, :algorithmVersion, :seedDigest,
                     CAST(:responseJson AS JSON), CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3))
                    """, new MapSqlParameterSource()
                    .addValue("studentId", studentId)
                    .addValue("batchId", batchId)
                    .addValue("clientRequestId", requestId)
                    .addValue("strategy", strategy.name())
                    .addValue("candidateVersion", candidateVersion)
                    .addValue("algorithmVersion", requiredValue(generated, "algorithmVersion"))
                    .addValue("seedDigest", requiredValue(generated, "seedDigest"))
                    .addValue("responseJson", json(generated)));
            return generated;
        } catch (DuplicateKeyException race) {
            List<Map<String, Object>> winner = find(
                    studentId,
                    batchId,
                    requestId,
                    candidateVersion,
                    false);
            if (winner.isEmpty()) {
                throw race;
            }
            return existingResponse(winner.getFirst(), strategy);
        }
    }

    private List<Map<String, Object>> find(
            long studentId,
            long batchId,
            String requestId,
            String candidateVersion,
            boolean lock) {
        return jdbc.queryForList("""
                SELECT strategy, response_json
                FROM student_recommendation_request
                WHERE student_id=:studentId
                  AND batch_id=:batchId
                  AND client_request_id=:clientRequestId
                  AND candidate_version=:candidateVersion
                """ + (lock ? " FOR UPDATE" : ""), new MapSqlParameterSource()
                .addValue("studentId", studentId)
                .addValue("batchId", batchId)
                .addValue("clientRequestId", requestId)
                .addValue("candidateVersion", candidateVersion));
    }

    private Map<String, Object> existingResponse(
            Map<String, Object> row,
            RecommendationStrategy requestedStrategy) {
        requireSameStrategy(String.valueOf(row.get("strategy")), requestedStrategy);
        return readMap(row.get("response_json"));
    }

    static String normalizeClientRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!CLIENT_REQUEST_ID.matcher(normalized).matches()) {
            throw new BusinessException(
                    "RECOMMENDATION_REQUEST_ID_INVALID",
                    "推荐请求编号格式不正确，请刷新页面后重试",
                    HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    static void requireSameStrategy(
            String storedStrategy,
            RecommendationStrategy requestedStrategy) {
        if (!requestedStrategy.name().equals(storedStrategy)) {
            throw new BusinessException(
                    "RECOMMENDATION_REQUEST_STRATEGY_CONFLICT",
                    "同一推荐请求编号不能更换推荐方式",
                    HttpStatus.CONFLICT);
        }
    }

    private String requiredValue(Map<String, Object> response, String key) {
        Object value = response.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new BusinessException(
                    "RECOMMENDATION_RESPONSE_INVALID",
                    "推荐结果缺少必要版本信息",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return String.valueOf(value);
    }

    private String json(Map<String, Object> response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    "RECOMMENDATION_RESPONSE_INVALID",
                    "推荐结果无法保存",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private Map<String, Object> readMap(Object value) {
        try {
            String json = value instanceof byte[] bytes
                    ? new String(bytes, StandardCharsets.UTF_8)
                    : String.valueOf(value);
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(
                    "RECOMMENDATION_RESPONSE_INVALID",
                    "已保存的推荐结果无法读取",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
