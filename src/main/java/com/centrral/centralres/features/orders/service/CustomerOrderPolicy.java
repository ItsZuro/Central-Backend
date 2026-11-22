package com.centrral.centralres.features.orders.service;

import java.util.Set;

/** Reglas centralizadas de cancelación y pago del checkout web. */
public final class CustomerOrderPolicy {
    private static final Set<String> CANCELLABLE = Set.of("PENDING", "PENDING_CONFIRMATION");
    private static final Set<String> DELIVERY_REQUIRES_PAID = Set.of(
            "CONFIRMED", "IN_PROGRESS", "READY_FOR_PICKUP", "OUT_FOR_DELIVERY",
            "DELIVERED", "COMPLETED");

    private CustomerOrderPolicy() {}

    public static boolean canCustomerCancel(String status, boolean hasConfirmedPayment) {
        return status != null && CANCELLABLE.contains(status.toUpperCase())
                && !hasConfirmedPayment;
    }

    public static boolean requiresConfirmedDeliveryPayment(String type, String targetStatus) {
        return "DELIVERY".equalsIgnoreCase(type) && targetStatus != null
                && DELIVERY_REQUIRES_PAID.contains(targetStatus.toUpperCase());
    }
}
