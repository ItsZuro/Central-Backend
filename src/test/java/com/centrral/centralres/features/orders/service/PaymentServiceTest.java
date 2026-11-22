package com.centrral.centralres.features.orders.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Set;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import com.centrral.centralres.core.config.NiubizConfig;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.PaymentProfileRepository;
import com.centrral.centralres.features.customers.model.Customer;
import com.centrral.centralres.features.orders.dto.payment.request.PaymentStartRequest;
import com.centrral.centralres.features.orders.enums.PaymentStatus;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.model.OrderType;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.OrderStatusRepository;
import com.centrral.centralres.features.orders.repository.PaymentMethodRepository;
import com.centrral.centralres.features.orders.repository.PaymentRepository;

class PaymentServiceTest {

    @Mock private PaymentRepository payments;
    @Mock private OrderRepository orders;
    @Mock private PaymentMethodRepository paymentMethods;
    @Mock private PaymentProfileRepository profiles;
    @Mock private OrderStatusRepository statuses;
    @Mock private NiubizService niubiz;
    @Mock private NiubizConfig config;

    private PaymentService service;
    private Order order;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new PaymentService(payments, orders, paymentMethods, profiles, statuses, niubiz, config);
        User owner = User.builder().id(1L).roles(Set.of()).build();
        order = Order.builder().id(23L).customer(Customer.builder().user(owner).build())
                .type(OrderType.builder().code("TAKE_AWAY").build())
                .status(OrderStatus.builder().code("PENDING").build())
                .total(new BigDecimal("32.00")).build();
        when(orders.findById(23L)).thenReturn(Optional.of(order));
    }

    @Test
    void differentCustomerCannotInitiateCardPaymentForOthersOrder() {
        User other = User.builder().id(2L).roles(Set.of()).build();
        assertThatThrownBy(() -> service.startOnlinePayment(
                PaymentStartRequest.builder().orderId(23L).amount(new BigDecimal("32.00")).build(), other))
                .isInstanceOf(AccessDeniedException.class);
        verify(niubiz, never()).createPaymentSession(any(), any());
    }

    @Test
    void rejectsBrowserProvidedAmountMismatchBeforeNiubizCall() {
        User owner = order.getCustomer().getUser();
        assertThatThrownBy(() -> service.startOnlinePayment(
                PaymentStartRequest.builder().orderId(23L).amount(new BigDecimal("1.00")).build(), owner))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("importe");
        verify(niubiz, never()).createPaymentSession(any(), any());
    }

    @Test
    void cannotStartPaymentForCancelledOrder() {
        User owner = order.getCustomer().getUser();
        order.setStatus(OrderStatus.builder().code("CANCELLED").build());
        assertThatThrownBy(() -> service.startOnlinePayment(
                PaymentStartRequest.builder().orderId(23L).amount(new BigDecimal("32.00")).build(), owner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no admite pagos");
        verify(niubiz, never()).createPaymentSession(any(), any());
    }

    @Test
    void cannotStartNewSessionAfterConfirmedPayment() {
        User owner = order.getCustomer().getUser();
        when(payments.existsByOrder_IdAndStatus(23L, PaymentStatus.CONFIRMED)).thenReturn(true);
        assertThatThrownBy(() -> service.startOnlinePayment(
                PaymentStartRequest.builder().orderId(23L).amount(new BigDecimal("32.00")).build(), owner))
                .isInstanceOf(IllegalStateException.class);
        verify(niubiz, never()).createPaymentSession(any(), any());
    }
}
