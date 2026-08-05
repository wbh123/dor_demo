package com.wust.dormitory.student;

import com.wust.dormitory.common.response.ResponseFactory;
import com.wust.dormitory.matching.RecommendationStrategy;
import com.wust.dormitory.model.api.StudentRecommendationApi;
import com.wust.dormitory.model.dto.ObjectSuccessResponse;
import com.wust.dormitory.model.dto.RecommendationRequest;
import com.wust.dormitory.security.CurrentUser;
import com.wust.dormitory.security.SecurityUsers;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class StudentRecommendationController implements StudentRecommendationApi {
    private final StudentRoomRecommendationService recommendationService;
    private final RecommendationIdempotencyService idempotencyService;

    public StudentRecommendationController(
            StudentRoomRecommendationService recommendationService,
            RecommendationIdempotencyService idempotencyService) {
        this.recommendationService = recommendationService;
        this.idempotencyService = idempotencyService;
    }

    @Override
    public ResponseEntity<ObjectSuccessResponse> createStudentRecommendation(
            Long batchId,
            RecommendationRequest request) {
        CurrentUser user = SecurityUsers.requireStudent();
        RecommendationStrategy strategy = RecommendationStrategy.valueOf(
                request.getStrategy().getValue());
        Map<String, Object> generated = recommendationService.recommend(
                batchId,
                strategy,
                user);
        Map<String, Object> result = idempotencyService.persistOrGet(
                user.studentId(),
                batchId,
                request.getClientRequestId(),
                strategy,
                generated);
        return ResponseEntity.ok(ResponseFactory.object(result));
    }
}
