package com.wust.dormitory.matching;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MatchingService {
    private static final Map<String, Double> WEIGHTS = Map.ofEntries(
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

    private final ObjectMapper objectMapper;

    public MatchingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public MatchResult roomScore(String studentFeatureJson, List<String> roommateFeatureJson) {
        if (studentFeatureJson == null || roommateFeatureJson.isEmpty()) {
            return new MatchResult(100.0, List.of("当前为空房间，可优先选择床位"), List.of());
        }
        Map<String, Object> student = parse(studentFeatureJson);
        double total = 0;
        List<String> positives = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (String json : roommateFeatureJson) {
            Map<String, Object> roommate = parse(json);
            total += pairScore(student, roommate);
        }
        double score = Math.round(total / roommateFeatureJson.size() * 10.0) / 10.0;
        compareTag(student, roommateFeatureJson, "sleepTimeMinutes", 60, "入睡时间接近", "入睡时间差异较大", positives, warnings);
        compareTag(student, roommateFeatureJson, "cleaningFrequency", 1, "卫生习惯接近", "卫生频率存在差异", positives, warnings);
        compareTag(student, roommateFeatureJson, "gamingVoiceFrequency", 1, "娱乐语音习惯接近", "游戏或语音频率存在差异", positives, warnings);
        if (hasSmokingConflict(student, roommateFeatureJson)) {
            warnings.add("吸烟接受偏好存在冲突");
        }
        return new MatchResult(score, positives.stream().limit(3).toList(), warnings.stream().distinct().limit(3).toList());
    }

    public Map<String, Object> normalizeAnswers(Map<String, Object> answers) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.putAll(answers);
        return normalized;
    }

    private double pairScore(Map<String, Object> left, Map<String, Object> right) {
        double weightedDifference = 0;
        double maximum = 0;
        for (Map.Entry<String, Double> entry : WEIGHTS.entrySet()) {
            Double a = number(left.get(entry.getKey()));
            Double b = number(right.get(entry.getKey()));
            if (a == null || b == null) {
                continue;
            }
            double range = entry.getKey().contains("Minutes") ? 720.0 : 5.0;
            double difference = entry.getKey().contains("Minutes")
                    ? circularMinuteDifference(a, b)
                    : Math.abs(a - b);
            weightedDifference += Math.min(difference / range, 1.0) * entry.getValue();
            maximum += entry.getValue();
        }
        if (maximum == 0) {
            return 80.0;
        }
        double score = 100.0 * (1.0 - weightedDifference / maximum);
        if (smokingConflict(left.get("smokingAcceptance"), right.get("smokingAcceptance"))) {
            score -= 25.0;
        }
        return Math.max(0, Math.min(100, score));
    }

    private boolean hasSmokingConflict(Map<String, Object> student, List<String> roommates) {
        Object own = student.get("smokingAcceptance");
        return roommates.stream()
                .map(this::parse)
                .map(map -> map.get("smokingAcceptance"))
                .anyMatch(value -> smokingConflict(own, value));
    }

    private boolean smokingConflict(Object left, Object right) {
        String leftValue = left == null ? "ANY" : String.valueOf(left);
        String rightValue = right == null ? "ANY" : String.valueOf(right);
        return ("ACCEPT".equals(leftValue) && "REJECT".equals(rightValue))
                || ("REJECT".equals(leftValue) && "ACCEPT".equals(rightValue));
    }

    private void compareTag(Map<String, Object> student, List<String> roommates, String key,
                            double threshold, String positive, String warning,
                            List<String> positives, List<String> warnings) {
        Double own = number(student.get(key));
        if (own == null) {
            return;
        }
        double average = roommates.stream().map(this::parse).map(map -> number(map.get(key)))
                .filter(value -> value != null).mapToDouble(Double::doubleValue).average().orElse(own);
        double difference = key.contains("Minutes") ? circularMinuteDifference(own, average) : Math.abs(own - average);
        if (difference <= threshold) {
            positives.add(positive);
        } else {
            warnings.add(warning);
        }
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

    public record MatchResult(double score, List<String> matches, List<String> warnings) {
    }
}
