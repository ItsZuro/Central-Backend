package com.centrral.centralres.core.security.service;

import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.centrral.centralres.core.security.dto.BlacklistedToken;
import com.centrral.centralres.core.security.enums.TokenEventType;
import com.centrral.centralres.core.security.model.TokenAudit;
import com.centrral.centralres.core.security.repository.TokenAuditRepository;
import lombok.RequiredArgsConstructor;

/** Shared JWT revocation using PostgreSQL rather than Redis or local memory. */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {
    private final TokenAuditRepository repository;

    @Transactional
    public void blacklistToken(String token, String username, String reason, Instant expiresAt,
                               String ipAddress, String userAgent) {
        Instant now = Instant.now();
        if (token == null || token.isBlank() || expiresAt == null || !expiresAt.isAfter(now)) return;
        repository.save(TokenAudit.builder().token(fingerprint(token)).username(username)
                .eventType(TokenEventType.BLACKLISTED).timestamp(now).expiresAt(expiresAt)
                .reason(reason).ipAddress(ipAddress).userAgent(userAgent).build());
    }

    @Transactional(readOnly = true)
    public boolean isBlacklisted(String token) {
        return token != null && !token.isBlank()
            && repository.existsByTokenAndEventTypeAndExpiresAtAfter(
                fingerprint(token), TokenEventType.BLACKLISTED, Instant.now());
    }

    @Transactional(readOnly = true)
    public BlacklistedToken getBlacklistedInfo(String token) {
        if (token == null || token.isBlank()) return null;
        return repository.findFirstByTokenAndEventTypeAndExpiresAtAfterOrderByTimestampDesc(
                fingerprint(token), TokenEventType.BLACKLISTED, Instant.now())
            .map(audit -> new BlacklistedToken(token, audit.getUsername(),
                    audit.getTimestamp(), audit.getReason())).orElse(null);
    }

    @Transactional
    public void revokeAllTokensForRoles(List<String> roles) {
        Instant now = Instant.now();
        List<TokenAudit> active = repository.findAll().stream()
            .filter(t -> t.getExpiresAt() != null && t.getExpiresAt().isAfter(now))
            .filter(t -> t.getEventType() != TokenEventType.BLACKLISTED)
            .filter(t -> roles.stream().anyMatch(r -> t.getReason() != null && t.getReason().contains(r)))
            .toList();
        for (TokenAudit token : active) {
            token.setToken(fingerprint(token.getToken()));
            token.setEventType(TokenEventType.BLACKLISTED);
            token.setReason("Revocado automáticamente por fin de jornada");
        }
        repository.saveAll(active);
    }

    /**
     * Revoca los tokens de acceso aún vigentes emitidos al usuario. El login
     * registra los JWT en TokenAudit como ISSUED y el filtro comprueba su hash
     * en la lista negra. No exponemos tokens ni contraseñas a la respuesta.
     */
    @Transactional
    public void revokeIssuedAccessTokensForUser(String username, String reason) {
        Instant now = Instant.now();
        List<TokenAudit> issued = repository.findByUsernameAndEventTypeAndExpiresAtAfter(
                username, TokenEventType.ISSUED, now);
        for (TokenAudit audit : issued) {
            blacklistToken(audit.getToken(), username, reason, audit.getExpiresAt(), null, null);
        }
    }

    private static String fingerprint(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
