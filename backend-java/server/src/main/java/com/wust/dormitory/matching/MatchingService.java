package com.wust.dormitory.matching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            Map.entry("studyFrequency", 0.8),
            Map.entry("gamingVoiceFrequency", 1.1),
            Map.entry("socialActivity", 0.6)
    );
    private static final Map<String, Double> FALLBACK_RULES = Map.of(
            "smokingConflictPenalty", 25.0,
            "sleepTimeWarningMinutes", 60.0,
            "cleaningWarningDifference", 1.0,
            "gamingVoiceWarningDifference", 1.0
    );

    private final ObjectMapper objectMapper;
    private final MatchingSchemeService schemeService;

    public MatchingService(ObjectMapper objectMapper, MatchingSchemeService schemeService) {
        this.objectMapper = objectMapper;
        this.schemeService = schemeService;
    }

    /**
     * Resolves the immutable policy referenced by
     * selection_batch.matching_weight_scheme_id before calculating scores.
     */
    public MatchResult roomScore(
            long batchId,
            String studentFeatureJson,
            List<String> roommateFeatureJson) {
        MatchingSchemeService.Policy policy = schemeService.policyForBatch(batchId);
        return calculate(
                studentFeatureJson,
                roommateFeatureJson,
                policy.weights(),
                policy.conflictRules());
    }

    /**
     * Compatibility path used by frozen first-stage callers. New recommendation
     * flows always call the batch-aware overload above.
     */
    public MatchResult roomScore(String studentFeatureJson, List<String> roommateFeatureJson) {
        return calculate(
                studentFeatureJson,
                roommateFeatureJson,
                FALLBACK_WEIGHTS,
                FALLBACK_RULES);
    }

    public Map<String, Object> normalizeAnswers(Map<String, Object> answers) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.putAll(answers);
        return normalized;
    }

    private MatchResult calculate(
            String studentFeatureJson,
            List<String> roommateFeatureJson,
            Map<String, Double> weights,
            Map<String, Double> rules) {
        if (studentFeatureJson == null || studentFeatureJson.isBlank()) {
            return result(80.0, List.of("完成生活习惯问卷后可获得更准确的推荐"), List.of(), 0);
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

        Set<String> recommendationReasons = new LinkedHashSet<>();
        Set<String> conflictReasons = new LinkedHashSet<>();
        compareTag(
                student,
                roommates,
                "sleepTimeMinutes",
                rule(rules, "sleepTimeWarningMinutes", 60),
                "入睡时间接近",
                "入睡时间差异较大",
                recommendationReasons,
                conflictReasons);
        compareTag(
                student,
                roommates,
                "cleaningFrequency",
                rule(rules, "cleaningWarningDifference", 1),
                "卫生习惯接近",
                "卫生频率存在差异",
                recommendationReasons,
                conflictReasons);
        compareTag(
                student,
                roommates,
                "gamingVoiceFrequency",
                rule(rules, "gamingVoiceWarningDifference", 1),
                "娱乐语音习惯接近",
                "游戏或语音频率存在差异",
                recommendationReasons,
                conflictReasons);
        if (hasSmokingConflict(student, roommates)) {
            conflictReasons.add("吸烟接受偏好存在冲突");
        } else if (student.containsKey("smokingAcceptance")) {
            recommendationReasons.add("吸烟接受偏好无明显冲突");
        }

        return result(
                score,
                recommendationReasons.stream().limit(3).toList(),
                conflictReasons.stream().limit(3).toList(),
                dimensionCount);
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
            if (weight <= 0) {
                continue;
            }
            Double a = number(left.get(key));
            Double b = number(right.get(key));
            if (a == null || b == null) {
                continue;
            }
            double range = key.contains("Minutes") ? 720.0 : 5.0;
            double difference = key.contains("Minutes")
                    ? circularMinuteDifference(a, b)
                    : Math.abs(a - b);
            weightedDifference += Math.min(difference / range, 1.0) * weight;
            maximum += weight;
            dimensionCount++;
        }
        if (maximum == 0) {
            return new PairResult(80.0, 0);
        }
        double score = 100.0 * (1.0 - weightedDifference / maximum);
        if (smokingConflict(left.get("smokingAcceptance"), right.get("smokingAcceptance"))) {
            score -= rule(rules, "smokingConflictPenalty", 25);
        }
        return new PairResult(Math.max(0, Math.min(100, score)), dimensionCount);
    }

    private boolean hasSmokingConflict(
            Map<String, Object> student,
            List<Map<String, Object>> roommates) {
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
        if (own == null) {
            return;
        }
        double average = roommates.stream()
                .map(map -> number(map.get(key)))
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(own);
        double difference = key.contains("Minutes")
                ? circularMinuteDifference(own, average)
                : Math.abs(own - average);
        if (difference <= threshold) {
            positives.add(positive);
        } else {
            warnings.add(warning);
        }
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

    private record PairResult(double score, int dimensionCount) {
    }

    public record MatchResult(
            double score,
            List<String> matches,
            List<String> warnings,
            List<String> recommendationReasons,
            List<String> conflictReasons,
            int dimensionCount) {
    }
}
