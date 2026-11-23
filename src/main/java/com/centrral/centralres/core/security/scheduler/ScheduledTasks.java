package com.centrral.centralres.core.security.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.centrral.centralres.core.security.service.TokenAuditCleanupService;
import com.centrral.centralres.core.config.DistributedSchedulerLock;
import com.centrral.centralres.core.security.service.TokenBlacklistService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final TokenAuditCleanupService cleanupService;
    private final TokenBlacklistService tokenBlacklistService;
    private final DistributedSchedulerLock distributedLock;

    @Scheduled(cron = "0 0 0 1 * *")
    public void cleanupOldTokenAudits() {
        distributedLock.runOnce(11001002L, cleanupService::cleanupOldAudits);
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void revokeEmployeeTokensAtMidnight() {
        distributedLock.runOnce(11001003L, () -> tokenBlacklistService.revokeAllTokensForRoles(
                List.of("ROLE_EMPLOYEE", "ROLE_WAITER", "ROLE_CHEF", "ROLE_CASHIER")));
    }

}
