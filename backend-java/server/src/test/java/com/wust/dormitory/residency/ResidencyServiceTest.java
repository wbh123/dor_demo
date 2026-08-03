package com.wust.dormitory.residency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wust.dormitory.audit.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResidencyServiceTest {
    private NamedParameterJdbcTemplate jdbc;
    private ResidencyService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        service = new ResidencyService(
                jdbc,
                mock(ResidencyPolicyService.class),
                mock(AuditService.class),
                mock(ObjectMapper.class));
    }

    @Test
    void currentUsesTheBuildingPrimaryKeyAliasAndReturnsAStableEmptyResult() {
        when(jdbc.queryForList(anyString(), anyMap())).thenReturn(List.of());

        Map<String, Object> result = service.current(7L);

        assertThat(result).containsEntry("resident", false);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), anyMap());
        assertThat(sql.getValue())
                .contains("db.id AS building_id")
                .doesNotContain("db.building_id");
    }
}
