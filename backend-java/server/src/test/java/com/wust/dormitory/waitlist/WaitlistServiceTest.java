package com.wust.dormitory.waitlist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import com.wust.dormitory.common.error.BusinessException;
import com.wust.dormitory.residency.ResidencyPolicyService;
import com.wust.dormitory.residency.ResidencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WaitlistServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private WaitlistService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new WaitlistService(
                jdbc,
                mock(ResidencyPolicyService.class),
                mock(ResidencyService.class),
                mock(AuditService.class),
                new ObjectMapper());
    }

    @Test
    void defaultsToDisabledWhenPolicyIsMissing() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThat(service.policy())
                .containsEntry("enabled", false)
                .containsEntry("offerTtlMinutes", 30)
                .containsEntry("priorityMode", "PRIORITY_THEN_FIFO")
                .containsEntry("scanBatchSize", 50);
    }

    @Test
    void rejectsCandidateLookupWhenWaitlistIsDisabled() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.candidates(10L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("WAITLIST_DISABLED");
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });
    }

    @Test
    void rejectsInvalidPolicyBeforeDatabaseMutation() {
        assertThatThrownBy(() -> service.updateSettings(
                true, 2, "UNKNOWN", 0, "测试", null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("WAITLIST_POLICY_INVALID"));
    }
}
