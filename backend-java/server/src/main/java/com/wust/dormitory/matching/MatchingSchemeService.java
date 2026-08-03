package com.wust.dormitory.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MatchingSchemeService {
    public static final Set<String> SUPPORTED_WEIGHT_KEYS = Set.of(
            "sleepTimeMinutes",
            "wakeTimeMinutes",
            "napHabit",
            "sleepSensitivity",
            "noiseTolerance",
            "cleaningFrequency",
            "tidinessRequirement",
            "airConditionerTemperature",
            "ventilationPreference",
            "summerAirConditionerTemperature",
            "winterHeatingTemperature",
            "summerOvernightAirConditioner",
            "winterHeatingAcceptance",
            "afterLightsActivity",
            "alarmSnooze",
            "strongFoodOdorAcceptance",
            "studyFrequency",
            "gamingVoiceFrequency",
            "socialActivity",
            "smokingAcceptance",
            "bedPreference"
    );

    public static final Set<String> SUPPORTED_RULE_KEYS = Set.of(
            "smokingConflictPenalty",
            "sleepTimeWarningMinutes",
            "cleaningWarningDifference",
            "gamingVoiceWarningDifference"
    );

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");
    private static final TypeReference<Map<String, Double>> DOUBLE_MAP = new TypeReference<>() { };

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public MatchingSchemeService(
            NamedParameterJdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AuditService auditService) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT scheme.id, scheme.scheme_code, scheme.scheme_name,
                       scheme.revision, scheme.algorithm_version,
                       scheme.weights_json, scheme.conflict_rules_json,
                       scheme.enabled, scheme.version, scheme.change_reason,
                       scheme.published_at, scheme.created_at, scheme.updated_at,
                       creator.display_name AS created_by_name,
                       (SELECT COUNT(*) FROM selection_batch batch
                        WHERE batch.matching_weight_scheme_id=scheme.id) AS batch_count
                FROM matching_weight_scheme scheme
                LEFT JOIN app_user creator ON creator.id=scheme.created_by
                ORDER BY scheme.scheme_code, scheme.revision DESC
                """, Map.of());
        rows.forEach(this::expandJson);
        return rows;
    }

    @Transactional
    public Map<String, Object> create(CreateCommand command, CurrentUser operator) {
        validateCommon(
                command.schemeName(),
                command.algorithmVersion(),
                command.weights(),
                command.conflictRules(),
                command.reason());
        if (command.schemeCode() == null
                || !CODE_PATTERN.matcher(command.schemeCode().trim()).matches()) {
            throw new BusinessException(
                    "MATCHING_SCHEME_CODE_INVALID",
                    "方案编码只能包含大写字母、数字、下划线和连字符");
        }
        String code = command.schemeCode().trim();
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM matching_weight_scheme WHERE scheme_code=:code",
                Map.of("code", code), Integer.class);
        if (count != null && count > 0) {
            throw new BusinessException(
                    "MATCHING_SCHEME_CODE_CONFLICT",
                    "方案编码已经存在",
                    HttpStatus.CONFLICT);
        }
        if (command.activate()) {
            deactivateAll();
        }
        long id;
        try {
            id = insert(
                    code,
                    command.schemeName(),
                    1,
                    command.algorithmVersion(),
                    command.weights(),
                    command.conflictRules(),
                    command.activate(),
                    command.reason(),
                    operator.userId());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    "MATCHING_SCHEME_CODE_CONFLICT",
                    "方案编码已经存在",
                    HttpStatus.CONFLICT);
        }
        Map<String, Object> created = one(id);
        auditService.success(
                operator,
                "MATCHING_SCHEME_CREATE",
                "MATCHING_WEIGHT_SCHEME",
                id,
                command.reason().trim(),
                null,
                created);
        return created;
    }

    @Transactional
    public Map<String, Object> createRevision(
            long schemeId,
            RevisionCommand command,
            CurrentUser operator) {
        validateCommon(
                command.schemeName(),
                command.algorithmVersion(),
                command.weights(),
                command.conflictRules(),
                command.reason());
        Map<String, Object> source = oneForUpdate(schemeId);
        int currentVersion = ((Number) source.get("version")).intValue();
        if (currentVersion != command.expectedVersion()) {
            throw new BusinessException(
                    "MATCHING_SCHEME_VERSION_CONFLICT",
                    "匹配方案已经发生变化，请重新加载后再保存",
                    HttpStatus.CONFLICT);
        }

        Map<String, Object> sourceForAudit = new LinkedHashMap<>(source);
        expandJson(sourceForAudit);

        int claimed = jdbc.update("""
                UPDATE matching_weight_scheme
                SET version=version+1
                WHERE id=:id AND version=:expectedVersion
                """, new MapSqlParameterSource()
                .addValue("id", schemeId)
                .addValue("expectedVersion", command.expectedVersion()));
        if (claimed != 1) {
            throw new BusinessException(
                    "MATCHING_SCHEME_VERSION_CONFLICT",
                    "匹配方案已经发生变化，请重新加载后再保存",
                    HttpStatus.CONFLICT);
        }

        String code = String.valueOf(source.get("scheme_code"));
        Integer latestRevision = jdbc.queryForObject("""
                SELECT revision
                FROM matching_weight_scheme
                WHERE scheme_code=:code
                ORDER BY revision DESC
                LIMIT 1
                FOR UPDATE
                """, Map.of("code", code), Integer.class);
        int revision = (latestRevision == null ? 0 : latestRevision) + 1;
        if (command.activate()) {
            deactivateAll();
        }
        long id = insert(
                code,
                command.schemeName(),
                revision,
                command.algorithmVersion(),
                command.weights(),
                command.conflictRules(),
                command.activate(),
                command.reason(),
                operator.userId());
        Map<String, Object> created = one(id);
        auditService.success(
                operator,
                "MATCHING_SCHEME_REVISION_CREATE",
                "MATCHING_WEIGHT_SCHEME",
                id,
                command.reason().trim(),
                sourceForAudit,
                created);
        return created;
    }

    public Policy policyForBatch(long batchId) {
        Map<String, Object> row = jdbc.queryForMap("""
                SELECT scheme.id, scheme.scheme_code, scheme.scheme_name,
                       scheme.revision, scheme.algorithm_version,
                       scheme.weights_json, scheme.conflict_rules_json
                FROM selection_batch batch
                JOIN matching_weight_scheme scheme
                  ON scheme.id=batch.matching_weight_scheme_id
                WHERE batch.id=:batchId
                """, Map.of("batchId", batchId));
        return new Policy(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("scheme_code")),
                String.valueOf(row.get("scheme_name")),
                ((Number) row.get("revision")).intValue(),
                String.valueOf(row.get("algorithm_version")),
                readDoubleMap(jsonText(row.get("weights_json"))),
                readDoubleMap(jsonText(row.get("conflict_rules_json"))));
    }

    private long insert(
            String code,
            String name,
            int revision,
            String algorithmVersion,
            Map<String, Double> weights,
            Map<String, Double> rules,
            boolean activate,
            String reason,
            long operatorId) {
        GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO matching_weight_scheme
                (scheme_code, scheme_name, revision, algorithm_version,
                 weights_json, conflict_rules_json, enabled, version,
                 created_by, change_reason, published_at)
                VALUES
                (:code, :name, :revision, :algorithmVersion,
                 CAST(:weights AS JSON), CAST(:rules AS JSON), :enabled, 0,
                 :createdBy, :reason, :publishedAt)
                """, new MapSqlParameterSource()
                .addValue("code", code)
                .addValue("name", name.trim())
                .addValue("revision", revision)
                .addValue("algorithmVersion", algorithmVersion.trim())
                .addValue("weights", json(weights))
                .addValue("rules", json(rules))
                .addValue("enabled", activate ? 1 : 0)
                .addValue("createdBy", operatorId)
                .addValue("reason", reason.trim())
                .addValue("publishedAt", activate ? LocalDateTime.now() : null),
                keyHolder,
                new String[]{"id"});
        return keyHolder.getKey().longValue();
    }

    private void deactivateAll() {
        jdbc.update("UPDATE matching_weight_scheme SET enabled=0 WHERE enabled=1", Map.of());
    }

    private void validateCommon(
            String name,
            String algorithmVersion,
            Map<String, Double> weights,
            Map<String, Double> rules,
            String reason) {
        if (name == null || name.isBlank() || name.length() > 128) {
            throw new BusinessException("MATCHING_SCHEME_NAME_INVALID", "方案名称长度不正确");
        }
        if (algorithmVersion == null || algorithmVersion.isBlank() || algorithmVersion.length() > 32) {
            throw new BusinessException("MATCHING_ALGORITHM_VERSION_INVALID", "算法版本长度不正确");
        }
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new BusinessException("MATCHING_SCHEME_REASON_REQUIRED", "请填写匹配方案修改原因");
        }
        validateWeights(weights);
        validateRules(rules);
    }

    private void validateWeights(Map<String, Double> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new BusinessException("MATCHING_WEIGHT_INVALID", "至少需要配置一个匹配权重");
        }
        for (String key : weights.keySet()) {
            if (!SUPPORTED_WEIGHT_KEYS.contains(key)) {
                throw new BusinessException(
                        "MATCHING_WEIGHT_KEY_INVALID",
                        "存在不支持的匹配维度：" + key);
            }
        }
        double total = 0;
        for (String key : SUPPORTED_WEIGHT_KEYS) {
            Double value = weights.getOrDefault(key, 0.0);
            if (value == null || !Double.isFinite(value) || value < 0 || value > 5) {
                throw new BusinessException(
                        "MATCHING_WEIGHT_INVALID",
                        "匹配权重必须在0到5之间");
            }
            total += value;
        }
        if (total <= 0) {
            throw new BusinessException("MATCHING_WEIGHT_INVALID", "至少一个匹配权重必须大于0");
        }
    }

    private void validateRules(Map<String, Double> rules) {
        if (rules == null || !rules.keySet().equals(SUPPORTED_RULE_KEYS)) {
            throw new BusinessException("MATCHING_RULE_INVALID", "冲突规则配置不完整或包含未知规则");
        }
        checkRule(rules, "smokingConflictPenalty", 0, 100);
        checkRule(rules, "sleepTimeWarningMinutes", 0, 720);
        checkRule(rules, "cleaningWarningDifference", 0, 5);
        checkRule(rules, "gamingVoiceWarningDifference", 0, 5);
    }

    private void checkRule(Map<String, Double> rules, String key, double min, double max) {
        Double value = rules.get(key);
        if (value == null || !Double.isFinite(value) || value < min || value > max) {
            throw new BusinessException("MATCHING_RULE_INVALID", "冲突规则数值超出允许范围：" + key);
        }
    }

    private Map<String, Object> one(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT scheme.id, scheme.scheme_code, scheme.scheme_name,
                       scheme.revision, scheme.algorithm_version,
                       scheme.weights_json, scheme.conflict_rules_json,
                       scheme.enabled, scheme.version, scheme.change_reason,
                       scheme.published_at, scheme.created_at, scheme.updated_at,
                       creator.display_name AS created_by_name,
                       (SELECT COUNT(*) FROM selection_batch batch
                        WHERE batch.matching_weight_scheme_id=scheme.id) AS batch_count
                FROM matching_weight_scheme scheme
                LEFT JOIN app_user creator ON creator.id=scheme.created_by
                WHERE scheme.id=:id
                """, Map.of("id", id));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "MATCHING_SCHEME_NOT_FOUND",
                    "匹配方案不存在",
                    HttpStatus.NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>(rows.getFirst());
        expandJson(result);
        return result;
    }

    private Map<String, Object> oneForUpdate(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM matching_weight_scheme
                WHERE id=:id
                FOR UPDATE
                """, Map.of("id", id));
        if (rows.isEmpty()) {
            throw new BusinessException(
                    "MATCHING_SCHEME_NOT_FOUND",
                    "匹配方案不存在",
                    HttpStatus.NOT_FOUND);
        }
        return new LinkedHashMap<>(rows.getFirst());
    }

    private void expandJson(Map<String, Object> row) {
        row.put("weights", readDoubleMap(jsonText(row.remove("weights_json"))));
        row.put("conflictRules", readDoubleMap(jsonText(row.remove("conflict_rules_json"))));
    }

    private String jsonText(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? "{}" : String.valueOf(value);
    }

    private Map<String, Double> readDoubleMap(String json) {
        try {
            return objectMapper.readValue(json, DOUBLE_MAP);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("MATCHING_SCHEME_DATA_INVALID", "匹配方案数据无法解析");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("MATCHING_SCHEME_DATA_INVALID", "匹配方案数据无法序列化");
        }
    }

    public record CreateCommand(
            String schemeCode,
            String schemeName,
            String algorithmVersion,
            Map<String, Double> weights,
            Map<String, Double> conflictRules,
            boolean activate,
            String reason) {
    }

    public record RevisionCommand(
            String schemeName,
            String algorithmVersion,
            Map<String, Double> weights,
            Map<String, Double> conflictRules,
            boolean activate,
            int expectedVersion,
            String reason) {
    }

    public record Policy(
            long id,
            String code,
            String name,
            int revision,
            String algorithmVersion,
            Map<String, Double> weights,
            Map<String, Double> conflictRules) {
    }
}
