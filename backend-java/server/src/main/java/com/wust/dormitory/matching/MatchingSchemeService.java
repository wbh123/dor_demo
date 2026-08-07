package com.wust.dormitory.matching;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.matching.mapper.MatchingSchemeMapper;
import com.wust.dormitory.security.CurrentUser;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class MatchingSchemeService {
    public static final Set<String> SUPPORTED_WEIGHT_KEYS = Set.of(
            "sleepTimeMinutes", "wakeTimeMinutes", "napHabit", "sleepSensitivity",
            "noiseTolerance", "cleaningFrequency", "tidinessRequirement",
            "airConditionerTemperature", "ventilationPreference",
            "summerAirConditionerTemperature", "winterHeatingTemperature",
            "summerOvernightAirConditioner", "winterHeatingAcceptance",
            "afterLightsActivity", "alarmSnooze", "strongFoodOdorAcceptance",
            "studyFrequency", "gamingVoiceFrequency", "socialActivity",
            "smokingAcceptance", "bedPreference");
    public static final Set<String> SUPPORTED_RULE_KEYS = Set.of(
            "smokingConflictPenalty", "sleepTimeWarningMinutes",
            "cleaningWarningDifference", "gamingVoiceWarningDifference");

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");
    private static final TypeReference<Map<String, Double>> DOUBLE_MAP = new TypeReference<>() { };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final MatchingSchemeMapper mapper;
    private final MatchingSchemePolicyCache policyCache;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public MatchingSchemeService(
            MatchingSchemeMapper mapper,
            MatchingSchemePolicyCache policyCache,
            ObjectMapper objectMapper,
            AuditService auditService) {
        this.mapper = mapper;
        this.policyCache = policyCache;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = mapper.findSchemes();
        rows.forEach(this::expandJson);
        return rows;
    }

    @Transactional
    public Map<String, Object> create(CreateCommand command, CurrentUser operator) {
        validateCommon(command.schemeName(), command.algorithmVersion(), command.weights(),
                command.conflictRules(), command.allowedRecommendationStrategies(),
                command.defaultRecommendationStrategy(), command.weightedRandomBaseWeight(),
                command.weightedRandomTemperature(), command.reason());
        if (command.schemeCode() == null || !CODE_PATTERN.matcher(command.schemeCode().trim()).matches()) {
            throw new BusinessException("MATCHING_SCHEME_CODE_INVALID", "方案编码只能包含大写字母、数字、下划线和连字符");
        }
        String code = command.schemeCode().trim();
        if (mapper.countSchemeCode(code) > 0) {
            throw new BusinessException("MATCHING_SCHEME_CODE_CONFLICT", "方案编码已经存在", HttpStatus.CONFLICT);
        }
        if (command.activate()) mapper.deactivateAll();
        long id;
        try {
            id = insert(code, command.schemeName(), 1, command.algorithmVersion(), command.weights(),
                    command.conflictRules(), command.allowedRecommendationStrategies(),
                    command.defaultRecommendationStrategy(), command.weightedRandomBaseWeight(),
                    command.weightedRandomTemperature(), command.activate(), command.reason(), operator.userId());
        } catch (DuplicateKeyException exception) {
            throw new BusinessException("MATCHING_SCHEME_CODE_CONFLICT", "方案编码已经存在", HttpStatus.CONFLICT);
        }
        policyCache.invalidate(id);
        Map<String, Object> created = one(id);
        auditService.success(operator, "MATCHING_SCHEME_CREATE", "MATCHING_WEIGHT_SCHEME",
                id, command.reason().trim(), null, created);
        return created;
    }

    @Transactional
    public Map<String, Object> createRevision(long schemeId, RevisionCommand command, CurrentUser operator) {
        validateCommon(command.schemeName(), command.algorithmVersion(), command.weights(),
                command.conflictRules(), command.allowedRecommendationStrategies(),
                command.defaultRecommendationStrategy(), command.weightedRandomBaseWeight(),
                command.weightedRandomTemperature(), command.reason());
        Map<String, Object> source = oneForUpdate(schemeId);
        if (integer(source.get("version")) != command.expectedVersion()) throw versionConflict();
        Map<String, Object> sourceForAudit = new LinkedHashMap<>(source);
        expandJson(sourceForAudit);
        if (mapper.claimVersion(schemeId, command.expectedVersion()) != 1) throw versionConflict();
        Integer latestRevision = mapper.findLatestRevisionForUpdate(String.valueOf(source.get("scheme_code")));
        int revision = (latestRevision == null ? 0 : latestRevision) + 1;
        if (command.activate()) mapper.deactivateAll();
        long id = insert(String.valueOf(source.get("scheme_code")), command.schemeName(), revision,
                command.algorithmVersion(), command.weights(), command.conflictRules(),
                command.allowedRecommendationStrategies(), command.defaultRecommendationStrategy(),
                command.weightedRandomBaseWeight(), command.weightedRandomTemperature(), command.activate(),
                command.reason(), operator.userId());
        policyCache.invalidate(schemeId);
        policyCache.invalidate(id);
        Map<String, Object> created = one(id);
        auditService.success(operator, "MATCHING_SCHEME_REVISION_CREATE", "MATCHING_WEIGHT_SCHEME",
                id, command.reason().trim(), sourceForAudit, created);
        return created;
    }

    public Policy policyForBatch(long batchId) {
        return policyCache.policyForBatch(batchId);
    }

    private long insert(
            String code, String name, int revision, String algorithmVersion,
            Map<String, Double> weights, Map<String, Double> rules, List<String> allowedStrategies,
            String defaultStrategy, double baseWeight, double temperature, boolean activate,
            String reason, long operatorId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", code);
        values.put("name", name.trim());
        values.put("revision", revision);
        values.put("algorithmVersion", algorithmVersion.trim());
        values.put("weights", json(weights));
        values.put("rules", json(rules));
        values.put("allowedStrategies", json(allowedStrategies));
        values.put("defaultStrategy", defaultStrategy);
        values.put("baseWeight", baseWeight);
        values.put("temperature", temperature);
        values.put("enabled", activate ? 1 : 0);
        values.put("createdBy", operatorId);
        values.put("reason", reason.trim());
        values.put("publishedAt", activate ? LocalDateTime.now() : null);
        mapper.insertScheme(values);
        Object id = values.get("id");
        if (!(id instanceof Number number)) throw new IllegalStateException("匹配方案创建成功但没有返回编号");
        return number.longValue();
    }

    private void validateCommon(
            String name, String algorithmVersion, Map<String, Double> weights, Map<String, Double> rules,
            List<String> allowedStrategies, String defaultStrategy, double baseWeight, double temperature,
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
        validateRecommendationPolicy(allowedStrategies, defaultStrategy, baseWeight, temperature);
    }

    static void validateRecommendationPolicy(
            List<String> allowedStrategies, String defaultStrategy, double baseWeight, double temperature) {
        if (allowedStrategies == null || allowedStrategies.isEmpty()) {
            throw new BusinessException("RECOMMENDATION_STRATEGY_REQUIRED", "至少保留一种推荐方式");
        }
        Set<String> unique = new LinkedHashSet<>(allowedStrategies);
        if (unique.size() != allowedStrategies.size()) {
            throw new BusinessException("RECOMMENDATION_STRATEGY_DUPLICATE", "推荐方式不能重复");
        }
        for (String strategy : unique) {
            try { RecommendationStrategy.valueOf(strategy); }
            catch (IllegalArgumentException exception) {
                throw new BusinessException("RECOMMENDATION_STRATEGY_INVALID", "存在未知推荐方式：" + strategy);
            }
        }
        if (defaultStrategy == null || !unique.contains(defaultStrategy)) {
            throw new BusinessException("RECOMMENDATION_DEFAULT_STRATEGY_INVALID", "默认推荐方式必须属于允许方式集合");
        }
        if (!Double.isFinite(baseWeight) || baseWeight <= 0.0d || baseWeight > 10.0d) {
            throw new BusinessException("RECOMMENDATION_BASE_WEIGHT_INVALID", "基础权重必须大于0且不超过10");
        }
        if (!Double.isFinite(temperature) || temperature <= 0.0d || temperature > 10.0d) {
            throw new BusinessException("RECOMMENDATION_TEMPERATURE_INVALID", "温度参数必须大于0且不超过10");
        }
    }

    private void validateWeights(Map<String, Double> weights) {
        if (weights == null || weights.isEmpty()) throw new BusinessException("MATCHING_WEIGHT_INVALID", "至少需要配置一个匹配权重");
        for (String key : weights.keySet()) {
            if (!SUPPORTED_WEIGHT_KEYS.contains(key)) throw new BusinessException("MATCHING_WEIGHT_KEY_INVALID", "存在不支持的匹配维度：" + key);
        }
        double total = 0;
        for (String key : SUPPORTED_WEIGHT_KEYS) {
            Double value = weights.getOrDefault(key, 0.0);
            if (value == null || !Double.isFinite(value) || value < 0 || value > 5) {
                throw new BusinessException("MATCHING_WEIGHT_INVALID", "匹配权重必须在0到5之间");
            }
            total += value;
        }
        if (total <= 0) throw new BusinessException("MATCHING_WEIGHT_INVALID", "至少一个匹配权重必须大于0");
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
        Map<String, Object> row = mapper.findScheme(id);
        if (row == null || row.isEmpty()) throw notFound();
        Map<String, Object> result = new LinkedHashMap<>(row);
        expandJson(result);
        return result;
    }

    private Map<String, Object> oneForUpdate(long id) {
        Map<String, Object> row = mapper.findSchemeForUpdate(id);
        if (row == null || row.isEmpty()) throw notFound();
        return new LinkedHashMap<>(row);
    }

    private void expandJson(Map<String, Object> row) {
        row.put("weights", readDoubleMap(jsonText(row.remove("weights_json"))));
        row.put("conflictRules", readDoubleMap(jsonText(row.remove("conflict_rules_json"))));
        row.put("allowedRecommendationStrategies", readStrategyNames(jsonText(row.remove("allowed_recommendation_strategies_json"))));
        row.put("defaultRecommendationStrategy", row.remove("default_recommendation_strategy"));
        row.put("weightedRandomBaseWeight", row.remove("weighted_random_base_weight"));
        row.put("weightedRandomTemperature", row.remove("weighted_random_temperature"));
    }

    private String jsonText(Object value) {
        if (value instanceof byte[] bytes) return new String(bytes, StandardCharsets.UTF_8);
        return value == null ? "{}" : String.valueOf(value);
    }

    private Map<String, Double> readDoubleMap(String json) {
        try { return objectMapper.readValue(json, DOUBLE_MAP); }
        catch (JsonProcessingException exception) { throw new BusinessException("MATCHING_SCHEME_DATA_INVALID", "匹配方案数据无法解析"); }
    }

    private List<RecommendationStrategy> readStrategyNames(String json) {
        try { return objectMapper.readValue(json, STRING_LIST).stream().map(RecommendationStrategy::valueOf).toList(); }
        catch (JsonProcessingException | IllegalArgumentException exception) { throw new BusinessException("MATCHING_SCHEME_DATA_INVALID", "推荐策略配置无法解析"); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new BusinessException("MATCHING_SCHEME_DATA_INVALID", "匹配方案数据无法序列化"); }
    }

    private int integer(Object value) { return ((Number) value).intValue(); }
    private BusinessException notFound() { return new BusinessException("MATCHING_SCHEME_NOT_FOUND", "匹配方案不存在", HttpStatus.NOT_FOUND); }
    private BusinessException versionConflict() { return new BusinessException("MATCHING_SCHEME_VERSION_CONFLICT", "匹配方案已经发生变化，请重新加载后再保存", HttpStatus.CONFLICT); }

    public record CreateCommand(
            String schemeCode, String schemeName, String algorithmVersion,
            Map<String, Double> weights, Map<String, Double> conflictRules,
            List<String> allowedRecommendationStrategies, String defaultRecommendationStrategy,
            double weightedRandomBaseWeight, double weightedRandomTemperature,
            boolean activate, String reason) { }

    public record RevisionCommand(
            String schemeName, String algorithmVersion, Map<String, Double> weights,
            Map<String, Double> conflictRules, List<String> allowedRecommendationStrategies,
            String defaultRecommendationStrategy, double weightedRandomBaseWeight,
            double weightedRandomTemperature, boolean activate, int expectedVersion, String reason) { }

    public record Policy(
            long id, String code, String name, int revision, String algorithmVersion,
            Map<String, Double> weights, Map<String, Double> conflictRules,
            List<RecommendationStrategy> allowedRecommendationStrategies,
            RecommendationStrategy defaultRecommendationStrategy,
            double weightedRandomBaseWeight, double weightedRandomTemperature) { }
}
