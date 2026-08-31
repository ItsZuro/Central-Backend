package com.centrral.centralres.core.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final String TEST_SECRET =
            "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 3_600_000L);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 1_209_600_000L);
    }

    @Test
    void shouldGenerateAndValidateAccessToken() {
        String token = jwtService.generateAccessToken("admin", Map.of("role", "ADMIN"));

        assertThat(jwtService.validate(token)).isTrue();
        assertThat(jwtService.extractSubject(token)).isEqualTo("admin");
    }

    @Test
    void shouldGenerateValidRefreshToken() {
        String token = jwtService.generateRefreshToken("cliente1");

        assertThat(jwtService.validate(token)).isTrue();
        assertThat(jwtService.extractUsername(token)).isEqualTo("cliente1");
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThat(jwtService.validate("not-a-valid-token")).isFalse();
    }

    @Test
    void shouldReportPositiveRemainingExpiration() {
        String token = jwtService.generateAccessToken("admin", Map.of());

        assertThat(jwtService.getExpirationMillis(token)).isPositive();
    }
}
