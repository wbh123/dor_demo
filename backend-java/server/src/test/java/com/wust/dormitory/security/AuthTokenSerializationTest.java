package com.wust.dormitory.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthTokenSerializationTest {
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void currentUserTokenPayloadCanRoundTrip() throws Exception {
        CurrentUser original = new CurrentUser(
                1L,
                null,
                "admin",
                "系统管理员",
                "ADMIN"
        );

        String payload = objectMapper.writeValueAsString(original);
        CurrentUser restored = objectMapper.readValue(payload, CurrentUser.class);

        assertThat(restored).isEqualTo(original);
        assertThat(payload)
                .doesNotContain("\"admin\":")
                .doesNotContain("\"student\":");
    }
}
