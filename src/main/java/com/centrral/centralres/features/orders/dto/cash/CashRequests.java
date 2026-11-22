package com.centrral.centralres.features.orders.dto.cash;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CashRequests {
    private CashRequests() {}

    public record Open(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal openingCash) {}

    public record Close(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal countedCash,
        @Size(max = 500) String note) {}

    public record Movement(
        @NotBlank String type,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotBlank @Size(min = 5, max = 500) String reason) {}

    public record Collect(
        @NotNull Long paymentMethodId,
        @Size(max = 100) String reference) {}
}
