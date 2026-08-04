package com.wust.dormitory.roomexchange;

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

class RoomExchangeServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private RoomExchangeService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new RoomExchangeService(
                jdbc,
                mock(ResidencyPolicyService.class),
                mock(ResidencyService.class),
                mock(AuditService.class));
    }

    @Test
    void defaultsToDisabledWhenPolicySettingIsMissing() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThat(service.policy())
                .containsEntry("mode", "DISABLED")
                .containsEntry("enabled", false)
                .containsEntry("requiresApproval", false);
    }

    @Test
    void rejectsCandidateLookupWhenExchangeIsDisabled() {
        when(jdbc.query(anyString(), anyMap(), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.candidates(10L))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("ROOM_EXCHANGE_DISABLED");
                    assertThat(exception.getStatus().value()).isEqualTo(409);
                });
    }

    @Test
    void rejectsUnknownPolicyModeBeforeDatabaseMutation() {
        assertThatThrownBy(() -> service.updateSettings(
                "UNKNOWN", "测试非法策略", null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode())
                                .isEqualTo("ROOM_EXCHANGE_MODE_INVALID"));
    }
}
