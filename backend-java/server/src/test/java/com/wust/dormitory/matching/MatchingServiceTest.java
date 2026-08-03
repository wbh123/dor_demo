package com.wust.dormitory.matching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.common.error.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class MatchingServiceTest {
    private MatchingService service;

    @BeforeEach
    void setUp() {
        service = new MatchingService(new ObjectMapper(), mock(MatchingSchemeService.class));
    }

    @Test
    void airConditionerTemperatureIsRestrictedToSixteenThroughThirty() {
        assertThat(service.normalizeAnswers(Map.of("summerAirConditionerTemperature", 16)))
                .containsEntry("summerAirConditionerTemperature", 16);
        assertThat(service.normalizeAnswers(Map.of("winterHeatingTemperature", 30)))
                .containsEntry("winterHeatingTemperature", 30);
        assertThatThrownBy(() -> service.normalizeAnswers(Map.of("airConditionerTemperature", 15)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("AIR_CONDITIONER_TEMPERATURE_INVALID"));
    }

    @Test
    void comparisonReturnsDetailedPositiveAndConflictTags() {
        MatchingService.MatchResult result = service.roomScore(
                "{\"sleepTimeMinutes\":1380,\"wakeTimeMinutes\":420,\"noiseTolerance\":1,\"tidinessRequirement\":5,\"studyFrequency\":5,\"socialActivity\":1}",
                List.of("{\"sleepTimeMinutes\":1410,\"wakeTimeMinutes\":600,\"noiseTolerance\":5,\"tidinessRequirement\":1,\"studyFrequency\":1,\"socialActivity\":5}"));

        assertThat(result.recommendationReasons()).contains("入睡时间接近");
        assertThat(result.conflictReasons())
                .contains("起床时间差异较大", "噪声接受度存在差异", "整洁要求存在差异", "学习频率存在差异");
    }
}
