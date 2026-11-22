package com.centrral.centralres.core.security.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.centrral.centralres.core.security.enums.TokenEventType;
import com.centrral.centralres.core.security.model.TokenAudit;

import jakarta.transaction.Transactional;

public interface TokenAuditRepository extends JpaRepository<TokenAudit, Long> {

    boolean existsByTokenAndEventTypeAndExpiresAtAfter(String token, TokenEventType eventType, Instant now);

    List<TokenAudit> findByUsernameAndEventTypeAndExpiresAtAfter(
            String username, TokenEventType eventType, Instant now);
    Optional<TokenAudit> findFirstByTokenAndEventTypeAndExpiresAtAfterOrderByTimestampDesc(
            String token, TokenEventType eventType, Instant now);

    /**
     * Elimina registros de auditoría antiguos según su timestamp.
     */
    @Transactional
    void deleteByTimestampBefore(Instant cutoffDate);

    /**
     * Encuentra tokens activos.
     * MEJORA: Ya no escribimos la ruta del paquete dentro del Query.
     * Pasamos el estado excluido (BLACKLISTED) como parámetro.
     */
    @Query("""
            SELECT t.token FROM TokenAudit t
            WHERE t.username = :username
            AND t.expiresAt > :now
            AND t.eventType <> :excludedStatus
            """)
    List<String> findActiveTokensByUsername(
            @Param("username") String username,
            @Param("now") Instant now,
            @Param("excludedStatus") TokenEventType excludedStatus);

}
