package com.centrral.centralres.core.security.scheduler;

import java.time.Instant;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.centrral.centralres.core.security.repository.RefreshTokenRepository;
import com.centrral.centralres.core.config.DistributedSchedulerLock;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshTokenCleaner {

    private final RefreshTokenRepository refreshTokenRepository;
    private final DistributedSchedulerLock distributedLock;

    // Se ejecuta cada 1 hora
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void cleanExpiredTokens() {
        distributedLock.runOnce(11001001L,
                () -> refreshTokenRepository.deleteByExpiryDateBefore(Instant.now()));
    }
}
