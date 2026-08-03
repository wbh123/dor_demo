package com.wust.dormitory.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {
    @Test
    void emptyConfigurationFallsBackToLocalDevelopmentOrigins() {
        assertThat(SecurityConfig.parseOriginPatterns(""))
                .containsExactly("http://localhost:*", "http://127.0.0.1:*");
    }

    @Test
    void configuredOriginsAreTrimmedDeduplicatedAndKeptInOrder() {
        assertThat(SecurityConfig.parseOriginPatterns(
                " https://demo.example.com , http://localhost:* , https://demo.example.com "))
                .containsExactly("https://demo.example.com", "http://localhost:*");
    }
}
