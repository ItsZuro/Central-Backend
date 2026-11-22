package com.centrral.centralres.features.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class CustomerOrderPolicyTest {
    @Test
    void pendingUnpaidCustomerMayCancel() {
        assertThat(CustomerOrderPolicy.canCustomerCancel("PENDING", false)).isTrue();
        assertThat(CustomerOrderPolicy.canCustomerCancel("PENDING_CONFIRMATION", false)).isTrue();
    }

    @Test
    void paidOrPreparingOrderCannotBeCancelledAutomatically() {
        assertThat(CustomerOrderPolicy.canCustomerCancel("PENDING", true)).isFalse();
        assertThat(CustomerOrderPolicy.canCustomerCancel("CONFIRMED", false)).isFalse();
        assertThat(CustomerOrderPolicy.canCustomerCancel("IN_PROGRESS", false)).isFalse();
        assertThat(CustomerOrderPolicy.canCustomerCancel("OUT_FOR_DELIVERY", false)).isFalse();
        assertThat(CustomerOrderPolicy.canCustomerCancel("DELIVERED", false)).isFalse();
    }

    @Test
    void deliveryMustBePaidBeforePreparationOrDispatch() {
        assertThat(CustomerOrderPolicy.requiresConfirmedDeliveryPayment("DELIVERY", "IN_PROGRESS")).isTrue();
        assertThat(CustomerOrderPolicy.requiresConfirmedDeliveryPayment("DELIVERY", "OUT_FOR_DELIVERY")).isTrue();
        assertThat(CustomerOrderPolicy.requiresConfirmedDeliveryPayment("DELIVERY", "CONFIRMED")).isTrue();
        assertThat(CustomerOrderPolicy.requiresConfirmedDeliveryPayment("DELIVERY", "PENDING")).isFalse();
        assertThat(CustomerOrderPolicy.requiresConfirmedDeliveryPayment("TAKE_AWAY", "IN_PROGRESS")).isFalse();
    }
}
