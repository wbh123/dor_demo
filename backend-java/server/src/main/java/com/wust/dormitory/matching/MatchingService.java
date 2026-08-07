package com.wust.dormitory.matching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MatchingService {
    private static final Map<String, Double> FALLBACK_WEIGHTS = Map.ofEntries(
            Map.entry("sleepTimeMinutes", 1.2),
            Map.entry("wakeTimeMinutes", 1.0),
            Map.entry("sleepSensitivity", 1.2),
            Map.entry("noiseTolerance", 1.2),
            Map.entry("cleaningFrequency", 1.0),
            Map.entry("tidinessRequirement", 1.0),
            Map.entry("airConditionerTemperature", 0.8),
            Map.entry("summerAirConditionerTemperature", 0.8),
            Map.entry("winterHeatingTemperature", 0.6),
            Map.entry("summerOvernightAirConditioner", 1.1),
            Map.entry("winterHeatingAcceptance", 0.8),
            Map.entry("afterLightsActivity", 1.2),
            Map.entry("alarmSnooze", 0.9),
            Map.entry("strongFoodOdorAcceptance", 0.7),
            Map.entry("studyFrequency", 0.8),
            Map.entry("gamingVoiceFrequency", 1.1),
            Map.entry("socialActivity", 0.6));
    private static final Map<String, Double> FALLBACK_RULES = Map.of(
            "smokingConflictPenalty", 25.0,
            "sleepTimeWarningMinutes", 60.0,
            "cleaningWarningDifference", 1.0,
            "gamingVoiceWarningDifference", 1.0);

    private final ObjectMapper objectMapper;
    private final MatchingSchemeService schemeService;

    public MatchingService(ObjectMapper objectMapper, MatchingSchemeService schemeService) {
        this.objectMapper = objectMapper;
        this.schemeService = schemeService;
    }

    public MatchingSchemeService.Policy policyForBatch(long batchId) {
        return schemeService.policyForBatch(batchId);
    }

    public MatchResult roomScore(long batchId, String studentFeatureJson, List<String> roommateFeatureJson) {
        return roomScore(policyForBatch(batchId), studentFeatureJson, roommateFeatureJson);
    }

    public MatchResult roomScore(
            MatchingSchemeService.Policy policy,
            String studentFeatureJson,
            List<String> roommateFeatureJson) {
        return calculate(studentFeatureJson, roommateFeatureJson, policy.weights(), policy.conflictRules());
    }

    public MatchResult roomScore(String studentFeatureJson, List<String> roommateFeatureJson) {
        return calculate(studentFeatureJson, roommateFeatureJson, FALLBACK_WEIGHTS, FALLBACK_RULES);
    }

    public Map<String, Object> normalizeAnswers(Map<String, Object> answers) {
        Map<String, Object> normalized = new LinkedHashMap<>(answers);
        for (String key : List.of(
                "airConditionerTemperature",
                "summerAirConditionerTemperature",
                "winterHeatingTemperature")) {
            Object value = normalized.get(key);
            if (value == null) continue;
            Double temperature = number(value);
            if (temperature == null || temperature < 16 || temperature > 30) {
                throw new BusinessException("AIR_CONDITIONER_TEMPERATURE_INVALID", "空调温度必须在16至30摄氏度之间");
            }
            normalized.put(key, temperature.intValue());
        }
        return normalized;
    }

    private MatchResult calculate(
            String studentFeatureJson,
            List<String> roommateFeatureJson,
            Map<String, Double> weights,
            Map<String, Double> rules) {
        if (studentFeatureJson == null || studentFeatureJson.isBlank()) {
            return result(80.0, List.of("完成个人偏好设置后可获得更准确的推荐"), List.of(), 0);
        }
        if (roommateFeatureJson.isEmpty()) {
            return result(100.0, List.of("当前为空房间，可优先选择床位"), List.of(), 0);
        }
        Map<String, Object> student = parse(studentFeatureJson);
        List<Map<String, Object>> roommates = roommateFeatureJson.stream()
                .map(this::parse)
                .filter(map -> !map.isEmpty())
                .toList();
        if (roommates.isEmpty()) {
            return result(100.0, List.of("当前房间暂无可比较的室友偏好"), List.of(), 0);
        }

        double total = 0;
        int dimensionCount = 0;
        for (Map<String, Object> roommate : roommates) {
            PairResult pair = pairScore(student, roommate, weights, rules);
            total += pair.score();
            dimensionCount = Math.max(dimensionCount, pair.dimensionCount());
        }
        double score = Math.round(total / roommates.size() * 10.0) / 10.0;
        Set<String> positives = new LinkedHashSet<>();
        Set<String> warnings = new LinkedHashSet<>();
        compareTag(student, roommates, "sleepTimeMinutes", rule(rules, "sleepTimeWarningMinutes", 60),
                "入睡时间接近", "入睡时间差异较大", positives, warnings);
        compareTag(student, roommates, "cleaningFrequency", rule(rules, "cleaningWarningDifference", 1),
                "卫生习惯接近", "卫生频率存在差异", positives, warnings);
        compareTag(student, roommates, "gamingVoiceFrequency", rule(rules, "gamingVoiceWarningDifference", 1),
                "娱乐语音习惯接近", "游戏或语音频率存在差异", positives, warnings);
        compareTag(student, roommates, "summerAirConditionerTemperature", 2,
                "夏季空调温度偏好接近", "空调使用偏好存在差异", positives, warnings);
        compareTag(student, roommates, "summerOvernightAirConditioner", 1,
                "夏季夜间空调习惯接近", "空调使用偏好存在差异", positives, warnings);
        compareTag(student, roommates, "winterHeatingAcceptance", 1,
                "冬季制热接受度接近", "空调使用偏好存在差异", positives, warnings);
        compareTag(student, roommates, "afterLightsActivity", 1,
                "熄灯后活动习惯接近", "熄灯后活动习惯存在差异", positives, warnings);
        compareTag(student, roommates, "alarmSnooze", 1,
                "闹钟习惯接近", "闹钟响铃习惯存在差异", positives, warnings);
        compareTag(student, roommates, "strongFoodOdorAcceptance", 1,
                "宿舍饮食气味接受度接近", "宿舍饮食气味接受度存在差异", positives, warnings);
        compareTag(student, roommates, "wakeTimeMinutes", 60,
                "起床时间接近", "起床时间差异较大", positives, warnings);
        compareTag(student, roommates, "noiseTolerance", 1,
                "噪声接受度接近", "噪声接受度存在差异", positives, warnings);
        compareTag(student, roommates, "tidinessRequirement", 1,
                "整洁要求接近", "整洁要求存在差异", positives, warnings);
        compareTag(student, roommates, "studyFrequency", 1,
                "学习频率接近", "学习频率存在差异", positives, warnings);
        compareTag(student, roommates, "socialActivity", 1,
                "社交活跃度接近", "社交活跃度存在差异", positives, warnings);
        if (hasSmokingConflict(student, roommates)) {
            warnings.add("吸烟接受偏好存在冲突");
        } else if (student.containsKey("smokingAcceptance")) {
            positives.add("吸烟接受偏好无明显冲突");
        }
        return result(score, positives.stream().limit(6).toList(), warnings.stream().limit(6).toList(), dimensionCount);
    }

    private MatchResult result(
            double score,
            List<String> recommendationReasons,
            List<String> conflictReasons,
            int dimensionCount) {
        return new MatchResult(
                Math.max(0, Math.min(100, score)),
                recommendationReasons,
                conflictReasons,
                recommendationReasons,
                conflictReasons,
                dimensionCount);
    }

    private PairResult pairScore(
            Map<String, Object> left,
            Map<String, Object> right,
            Map<String, Double> weights,
            Map<String, Double> rules) {
        double weightedDifference = 0;
        double maximum = 0;
        int dimensionCount = 0;
        for (String key : MatchingSchemeService.SUPPORTED_WEIGHT_KEYS) {
            double weight = weights.getOrDefault(key, 0.0);
            if (weight <= 0) continue;
            Double a = number(left.get(key));
            Double b = number(right.get(key));
            if (a == null || b == null) continue;
            double range = dimensionRange(key);
            double difference = key.contains("Minutes")
                    ? circularMinuteDifference(a, b)
                    : Math.abs(a - b);
            weightedDifference += Math.min(difference / range, 1.0) * weight;
            maximum += weight;
            dimensionCount++;
        }
        if (maximum == 0) return new PairResult(80.0, 0);
        double score = 100.0 * (1.0 - weightedDifference / maximum);
        if (smokingConflict(left.get("smokingAcceptance"), right.get("smokingAcceptance"))) {
            score -= rule(rules, "smokingConflictPenalty", 25);
        }
        return new PairResult(Math.max(0, Math.min(100, score)), dimensionCount);
    }

    private double dimensionRange(String key) {
        if (key.contains("Minutes")) return 720.0;
        if (key.contains("Temperature")) return 10.0;
        return 5.0;
    }

    private boolean hasSmokingConflict(Map<String, Object> student, List<Map<String, Object>> roommates) {
        Object own = student.get("smokingAcceptance");
        return roommates.stream()
                .map(map -> map.get("smokingAcceptance"))
                .anyMatch(value -> smokingConflict(own, value));
    }

    private boolean smokingConflict(Object left, Object right) {
        String leftValue = left == null ? "ANY" : String.valueOf(left);
        String rightValue = right == null ? "ANY" : String.valueOf(right);
        return ("ACCEPT".equals(leftValue) && "REJECT".equals(rightValue))
                || ("REJECT".equals(leftValue) && "ACCEPT".equals(rightValue));
    }

    private void compareTag(
            Map<String, Object> student,
            List<Map<String, Object>> roommates,
            String key,
            double threshold,
            String positive,
            String warning,
            Set<String> positives,
            Set<String> warnings) {
        Double own = number(student.get(key));
        if (own == null) return;
        double average = roommates.stream()
                .map(map -> number(map.get(key)))
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(own);
        double difference = key.contains("Minutes")
                ? circularMinuteDifference(own, average)
                : Math.abs(own - average);
        if (difference <= threshold) positives.add(positive);
        else warnings.add(warning);
    }

    private double rule(Map<String, Double> rules, String key, double fallback) {
        Double value = rules.get(key);
        return value == null || !Double.isFinite(value) ? fallback : value;
    }

    private Map<String, Object> parse(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private Double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private double circularMinuteDifference(double a, double b) {
        double difference = Math.abs(a - b);
        return Math.min(difference, 1440.0 - difference);
    }

    private record PairResult(double score, int dimensionCount) { }

    public record MatchResult(
            double score,
            List<String> matches,
            List<String> warnings,
            List<String> recommendationReasons,
            List<String> conflictReasons,
            int dimensionCount) { }
}
