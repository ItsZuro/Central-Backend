package com.centrral.centralres.features.orders.dto.cash;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CashShiftResponse(
    Long id, Long cashierUserId,
    Instant openedAt, Instant closedAt,
    BigDecimal openingCash,
    BigDecimal expectedCash,
    BigDecimal countedCash,
    BigDecimal difference,
    String closingNote,
    Map<String, BigDecimal> salesByMethod,
    BigDecimal cashIn,
    BigDecimal cashOut,
    int salesCount,
    List<Entry> entries) {

    public record Entry(
        Long id, Long orderId,
        String type, String methodCode,
        BigDecimal amount, String reference,
        String description, Instant at) {}
}
