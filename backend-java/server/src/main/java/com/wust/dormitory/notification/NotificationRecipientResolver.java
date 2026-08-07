package com.wust.dormitory.notification;

import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NotificationRecipientResolver {
    private final NotificationRecipientMapper mapper;

    public NotificationRecipientResolver(NotificationRecipientMapper mapper) {
        this.mapper = mapper;
    }

    public List<Long> resolve(RecipientCriteria criteria) {
        RecipientCriteria normalized = criteria == null
                ? RecipientCriteria.empty()
                : criteria.normalized();
        if (!normalized.studentIds().isEmpty()) {
            return normalized.studentIds();
        }
        return mapper.findRecipients(
                normalized.batchIds(),
                normalized.majorIds(),
                normalized.gradeYears(),
                normalized.degreeLevels(),
                normalized.studentCategories(),
                normalized.buildingIds(),
                normalized.unselectedOnly(),
                normalized.pendingReviewOnly());
    }

    public record RecipientCriteria(
            List<Long> studentIds,
            Long batchId,
            Long majorId,
            Integer gradeYear,
            String degreeLevel,
            String studentCategory,
            Long buildingId,
            boolean unselectedOnly,
            boolean pendingReviewOnly,
            List<Long> batchIds,
            List<Long> majorIds,
            List<Integer> gradeYears,
            List<String> degreeLevels,
            List<String> studentCategories,
            List<Long> buildingIds) {

        public RecipientCriteria(
                List<Long> studentIds,
                Long batchId,
                Long majorId,
                Integer gradeYear,
                String degreeLevel,
                String studentCategory,
                Long buildingId,
                boolean unselectedOnly,
                boolean pendingReviewOnly) {
            this(
                    studentIds, batchId, majorId, gradeYear,
                    degreeLevel, studentCategory, buildingId,
                    unselectedOnly, pendingReviewOnly,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        static RecipientCriteria empty() {
            return new RecipientCriteria(
                    List.of(), null, null, null, "", "", null,
                    false, false,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        RecipientCriteria normalized() {
            return new RecipientCriteria(
                    normalizeLongs(studentIds, null),
                    batchId,
                    majorId,
                    gradeYear,
                    clean(degreeLevel),
                    clean(studentCategory),
                    buildingId,
                    unselectedOnly,
                    pendingReviewOnly,
                    normalizeLongs(batchIds, batchId),
                    normalizeLongs(majorIds, majorId),
                    normalizeIntegers(gradeYears, gradeYear),
                    normalizeStrings(degreeLevels, degreeLevel),
                    normalizeStrings(studentCategories, studentCategory),
                    normalizeLongs(buildingIds, buildingId));
        }

        private static List<Long> normalizeLongs(List<Long> values, Long legacyValue) {
            Set<Long> unique = new LinkedHashSet<>(values == null ? List.of() : values);
            if (legacyValue != null) unique.add(legacyValue);
            unique.removeIf(value -> value == null || value <= 0);
            return unique.stream().sorted().toList();
        }

        private static List<Integer> normalizeIntegers(List<Integer> values, Integer legacyValue) {
            Set<Integer> unique = new LinkedHashSet<>(values == null ? List.of() : values);
            if (legacyValue != null) unique.add(legacyValue);
            unique.removeIf(value -> value == null || value <= 0);
            return unique.stream().sorted().toList();
        }

        private static List<String> normalizeStrings(List<String> values, String legacyValue) {
            Set<String> unique = new LinkedHashSet<>();
            if (values != null) {
                for (String value : values) {
                    String normalized = clean(value);
                    if (!normalized.isEmpty()) unique.add(normalized);
                }
            }
            String normalizedLegacy = clean(legacyValue);
            if (!normalizedLegacy.isEmpty()) unique.add(normalizedLegacy);
            return List.copyOf(unique);
        }

        private static String clean(String value) {
            return value == null ? "" : value.trim();
        }
    }
}
