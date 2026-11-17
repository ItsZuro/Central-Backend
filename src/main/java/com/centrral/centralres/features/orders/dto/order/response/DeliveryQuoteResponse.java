package com.centrral.centralres.features.orders.dto.order.response;

/** Server-verified road route and configurable delivery coverage. */
public record DeliveryQuoteResponse(
        double distanceKm,
        int durationMinutes,
        double maxDistanceKm
) {}
