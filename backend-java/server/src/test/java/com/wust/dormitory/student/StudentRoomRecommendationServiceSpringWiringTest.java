package com.wust.dormitory.student;

import com.wust.dormitory.matching.BatchRecommendationPolicyService;
import com.wust.dormitory.matching.MatchingService;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.student.mapper.StudentRoomRecommendationMapper;
import com.wust.dormitory.subscription.FeatureAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

class StudentRoomRecommendationServiceSpringWiringTest {
    @Test
    void springCanInstantiateRecommendationServiceWithItsApplicationConstructor() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.registerBean(StudentRoomRecommendationMapper.class, () -> mock(StudentRoomRecommendationMapper.class));
        context.registerBean(MatchingService.class, () -> mock(MatchingService.class));
        context.registerBean(ResidencyPolicyService.class, () -> mock(ResidencyPolicyService.class));
        context.registerBean(StudentPreferenceService.class, () -> mock(StudentPreferenceService.class));
        context.registerBean(FeatureAccessService.class, () -> mock(FeatureAccessService.class));
        context.registerBean(BatchRecommendationPolicyService.class, () -> mock(BatchRecommendationPolicyService.class));
        context.register(StudentRoomRecommendationService.class);
        try (context) {
            assertDoesNotThrow(context::refresh);
        }
    }
}
