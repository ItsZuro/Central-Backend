package com.centrral.centralres.features.customers.dto.customer.response;

import java.time.Instant;

public record CustomerAccountActionResponse(
        Long id, Long actorUserId, String action, String reason, Instant occurredAt) {}
