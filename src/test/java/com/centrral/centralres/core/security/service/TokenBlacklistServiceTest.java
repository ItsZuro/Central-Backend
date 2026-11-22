package com.centrral.centralres.core.security.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.util.Optional;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.centrral.centralres.core.security.enums.TokenEventType;
import com.centrral.centralres.core.security.model.TokenAudit;
import com.centrral.centralres.core.security.repository.TokenAuditRepository;

class TokenBlacklistServiceTest {
    @Mock private TokenAuditRepository repository;
    private TokenBlacklistService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new TokenBlacklistService(repository);
    }

    @Test
    void blacklistStoresHash() {
        Instant expires = Instant.now().plus(30, ChronoUnit.MINUTES);
        service.blacklistToken("example-jwt", "user", "logout", expires, null, null);
        ArgumentCaptor<TokenAudit> saved = ArgumentCaptor.forClass(TokenAudit.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getToken()).hasSize(43).isNotEqualTo("example-jwt");
        assertThat(saved.getValue().getEventType()).isEqualTo(TokenEventType.BLACKLISTED);
    }

    @Test
    void sharedRepositoryChecksRevocation() {
        when(repository.existsByTokenAndEventTypeAndExpiresAtAfter(
            org.mockito.ArgumentMatchers.anyString(),
            eq(TokenEventType.BLACKLISTED), any(Instant.class))).thenReturn(true);
        assertThat(service.isBlacklisted("revoked")).isTrue();
        assertThat(service.isBlacklisted(null)).isFalse();
    }

    @Test
    void expiredTokenIsNotSaved() {
        service.blacklistToken("expired", "user", "logout",
            Instant.now().minusSeconds(5), null, null);
        verify(repository, never()).save(any(TokenAudit.class));
    }
    @Test
    void passwordResetCreatesAuditMarkerWithoutSensitiveCredentials() {
        service.recordStaffPasswordReset("mesero.central");
        ArgumentCaptor<TokenAudit> saved = ArgumentCaptor.forClass(TokenAudit.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getEventType()).isEqualTo(TokenEventType.REVOKED);
        assertThat(saved.getValue().getReason()).isEqualTo("STAFF_PASSWORD_RESET");
        assertThat(saved.getValue().getUsername()).isEqualTo("mesero.central");
        assertThat(saved.getValue().getToken()).startsWith("credentials-reset-");
    }

    @Test
    void passwordResetInvalidatesTokensIssuedBeforeMarker() {
        Instant markerTime = Instant.parse("2026-11-22T20:00:00Z");
        TokenAudit marker = TokenAudit.builder().eventType(TokenEventType.REVOKED)
                .timestamp(markerTime).username("mesero.central")
                .reason("STAFF_PASSWORD_RESET").build();
        when(repository.findFirstByUsernameAndEventTypeAndReasonOrderByTimestampDesc(
                "mesero.central", TokenEventType.REVOKED, "STAFF_PASSWORD_RESET"))
                .thenReturn(Optional.of(marker));

        assertThat(service.tokenPredatesStaffPasswordReset(
                "mesero.central", markerTime.minusSeconds(1))).isTrue();
        assertThat(service.tokenPredatesStaffPasswordReset(
                "mesero.central", markerTime.plusSeconds(1))).isFalse();
    }

}
