package com.wust.dormitory.student;

import com.wust.dormitory.matching.BatchRecommendationPolicyService;
import com.wust.dormitory.matching.MatchingSchemeService;
import com.wust.dormitory.matching.MatchingService;
import com.wust.dormitory.matching.RecommendationStrategy;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.student.mapper.StudentRoomRecommendationMapper;
import com.wust.dormitory.student.model.persistence.RoomRecommendationCandidateRow;
import com.wust.dormitory.subscription.FeatureAccessService;
import com.wust.dormitory.subscription.FeatureCodes;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentRoomRecommendationServiceTest {
    @Test
    void candidateDatabaseCallsStayConstantWhenRoomCountGrows() {
        StudentRoomRecommendationMapper mapper = mock(StudentRoomRecommendationMapper.class);
        MatchingService matchingService = mock(MatchingService.class);
        ResidencyPolicyService policy = mock(ResidencyPolicyService.class);
        StudentPreferenceService preferenceService = mock(StudentPreferenceService.class);
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        BatchRecommendationPolicyService recommendationPolicyService = mock(BatchRecommendationPolicyService.class);
        BatchRecommendationPolicyService.Policy recommendationPolicy = new BatchRecommendationPolicyService.Policy(
                List.of(RecommendationStrategy.BEST_MATCH),
                RecommendationStrategy.BEST_MATCH,
                0.05d,
                0.20d);
        MatchingSchemeService.Policy matchingPolicy = new MatchingSchemeService.Policy(
                1L,
                "STANDARD",
                "Standard",
                1,
                "v1",
                Map.of(),
                Map.of(),
                List.of(RecommendationStrategy.BEST_MATCH),
                RecommendationStrategy.BEST_MATCH,
                0.05d,
                0.20d);
        MatchingService.MatchResult match = new MatchingService.MatchResult(
                100.0d,
                List.of(),
                List.of(),
                List.of("空房间"),
                List.of(),
                0);
        List<RoomRecommendationCandidateRow> candidates = LongStream.rangeClosed(1, 100)
                .mapToObj(StudentRoomRecommendationServiceTest::candidate)
                .toList();
        List<Long> roomIds = candidates.stream().map(RoomRecommendationCandidateRow::id).toList();

        when(mapper.isBatchAccessible(9L, 7L)).thenReturn(true);
        when(mapper.findBatchFeature(9L, 7L)).thenReturn("{}");
        when(mapper.findCandidateRooms(9L, null)).thenReturn(candidates);
        when(mapper.findRoommateFeatures(9L, roomIds)).thenReturn(List.of());
        when(mapper.findAvailableBedTypes(9L, roomIds)).thenReturn(List.of());
        when(policy.batch(9L)).thenReturn(Map.of("selection_mode", "ROOM", "separate_student_categories", 0));
        when(policy.student(7L)).thenReturn(Map.of("gender", "M", "student_category", "DOMESTIC"));
        when(recommendationPolicyService.forBatch(9L)).thenReturn(recommendationPolicy);
        when(featureAccessService.has(FeatureCodes.P2_ROOM_RECOMMENDATION)).thenReturn(true);
        when(matchingService.policyForBatch(9L)).thenReturn(matchingPolicy);
        when(preferenceService.completed(7L)).thenReturn(true);
        when(matchingService.roomScore(matchingPolicy, "{}", List.of())).thenReturn(match);

        StudentRoomRecommendationService service = new StudentRoomRecommendationService(
                mapper,
                matchingService,
                policy,
                preferenceService,
                featureAccessService,
                recommendationPolicyService,
                new SecureRandom());
        List<Map<String, Object>> result = service.rooms(9L, student());

        assertEquals(100, result.size());
        verify(mapper, times(1)).findCandidateRooms(9L, null);
        verify(mapper, times(1)).findRoommateFeatures(9L, roomIds);
        verify(mapper, times(1)).findAvailableBedTypes(9L, roomIds);
        verify(featureAccessService, times(1)).has(FeatureCodes.P2_ROOM_RECOMMENDATION);
        verify(preferenceService, times(1)).completed(7L);
        verify(matchingService, times(1)).policyForBatch(9L);
        verify(matchingService, times(100)).roomScore(matchingPolicy, "{}", List.of());
    }

    @Test
    void singleRoomLookupUsesTargetedCandidateQuery() {
        StudentRoomRecommendationMapper mapper = mock(StudentRoomRecommendationMapper.class);
        MatchingService matchingService = mock(MatchingService.class);
        ResidencyPolicyService policy = mock(ResidencyPolicyService.class);
        StudentPreferenceService preferenceService = mock(StudentPreferenceService.class);
        FeatureAccessService featureAccessService = mock(FeatureAccessService.class);
        BatchRecommendationPolicyService recommendationPolicyService = mock(BatchRecommendationPolicyService.class);
        RoomRecommendationCandidateRow room = candidate(5L);

        when(mapper.isBatchAccessible(9L, 7L)).thenReturn(true);
        when(mapper.findBatchFeature(9L, 7L)).thenReturn("{}");
        when(mapper.findCandidateRooms(9L, 5L)).thenReturn(List.of(room));
        when(mapper.findRoommateFeatures(9L, List.of(5L))).thenReturn(List.of());
        when(mapper.findAvailableBedTypes(9L, List.of(5L))).thenReturn(List.of());
        when(policy.batch(9L)).thenReturn(Map.of("selection_mode", "ROOM", "separate_student_categories", 0));
        when(policy.student(7L)).thenReturn(Map.of("gender", "M", "student_category", "DOMESTIC"));
        when(recommendationPolicyService.forBatch(9L)).thenReturn(new BatchRecommendationPolicyService.Policy(
                List.of(RecommendationStrategy.BEST_MATCH),
                RecommendationStrategy.BEST_MATCH,
                0.05d,
                0.20d));
        when(featureAccessService.has(FeatureCodes.P2_ROOM_RECOMMENDATION)).thenReturn(false);
        when(preferenceService.completed(7L)).thenReturn(true);
        when(matchingService.roomScore("", List.of())).thenReturn(new MatchingService.MatchResult(
                100.0d, List.of(), List.of(), List.of(), List.of(), 0));

        StudentRoomRecommendationService service = new StudentRoomRecommendationService(
                mapper,
                matchingService,
                policy,
                preferenceService,
                featureAccessService,
                recommendationPolicyService,
                new SecureRandom());
        Map<String, Object> result = service.room(9L, 5L, student());

        assertEquals(5L, ((Number) result.get("id")).longValue());
        verify(mapper).findCandidateRooms(9L, 5L);
        verify(mapper, never()).findCandidateRooms(9L, null);
        verify(matchingService, never()).policyForBatch(9L);
    }

    private static RoomRecommendationCandidateRow candidate(long id) {
        return new RoomRecommendationCandidateRow(
                id,
                "R" + id,
                "FOUR_PERSON",
                4,
                "M",
                "DOMESTIC_ONLY",
                "ENABLED",
                1L,
                1,
                1L,
                "B1",
                "Building 1",
                0,
                0,
                4);
    }

    private static CurrentUser student() {
        return new CurrentUser(100L, 7L, "student", "Student", "STUDENT");
    }
}
