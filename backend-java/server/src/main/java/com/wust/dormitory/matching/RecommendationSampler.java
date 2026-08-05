package com.wust.dormitory.matching;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * 对已经完成硬约束过滤的候选进行抽样。
 *
 * <p>本类不查询数据库，也不接受客户端种子；调用方负责生成服务端种子并记录摘要。</p>
 */
public final class RecommendationSampler {
    private RecommendationSampler() {
    }

    public record Candidate<T>(T value, double score, String stableKey) {
        public Candidate {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(stableKey, "stableKey");
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("score must be finite");
            }
        }
    }

    public static <T> Candidate<T> bestMatch(List<Candidate<T>> candidates) {
        requireCandidates(candidates);
        return candidates.stream()
                .min(Comparator
                        .<Candidate<T>>comparingDouble(Candidate::score)
                        .reversed()
                        .thenComparing(Candidate::stableKey))
                .orElseThrow();
    }

    public static <T> Candidate<T> trueRandom(
            List<Candidate<T>> candidates,
            RandomGenerator random) {
        requireCandidates(candidates);
        Objects.requireNonNull(random, "random");
        return candidates.get(random.nextInt(candidates.size()));
    }

    public static <T> Candidate<T> weightedRandom(
            List<Candidate<T>> candidates,
            RandomGenerator random,
            double baseWeight,
            double temperature) {
        requireCandidates(candidates);
        Objects.requireNonNull(random, "random");
        if (!Double.isFinite(baseWeight) || baseWeight <= 0.0d) {
            throw new IllegalArgumentException("baseWeight must be finite and greater than zero");
        }
        if (!Double.isFinite(temperature) || temperature <= 0.0d) {
            throw new IllegalArgumentException("temperature must be finite and greater than zero");
        }

        double maxScore = candidates.stream()
                .mapToDouble(Candidate::score)
                .max()
                .orElseThrow();
        double[] weights = new double[candidates.size()];
        double total = 0.0d;
        for (int index = 0; index < candidates.size(); index++) {
            double normalized = (candidates.get(index).score() - maxScore) / temperature;
            double weight = baseWeight + Math.exp(normalized);
            weights[index] = weight;
            total += weight;
        }
        if (!Double.isFinite(total) || total <= 0.0d) {
            throw new IllegalArgumentException("candidate weights are not numerically stable");
        }

        double cursor = random.nextDouble(total);
        for (int index = 0; index < weights.length; index++) {
            cursor -= weights[index];
            if (cursor < 0.0d) {
                return candidates.get(index);
            }
        }
        return candidates.getLast();
    }

    private static void requireCandidates(List<?> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("at least one legal candidate is required");
        }
    }
}
