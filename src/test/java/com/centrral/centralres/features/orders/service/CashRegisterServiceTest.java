package com.centrral.centralres.features.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;

import com.centrral.centralres.core.exceptions.BadRequestException;
import com.centrral.centralres.core.exceptions.ConflictException;
import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.core.security.repository.UserRepository;
import com.centrral.centralres.features.orders.dto.cash.CashRequests;
import com.centrral.centralres.features.orders.enums.PaymentStatus;
import com.centrral.centralres.features.orders.model.CashEntry;
import com.centrral.centralres.features.orders.model.CashShift;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.model.OrderType;
import com.centrral.centralres.features.orders.model.Payment;
import com.centrral.centralres.features.orders.model.PaymentMethod;
import com.centrral.centralres.features.orders.repository.CashEntryRepository;
import com.centrral.centralres.features.orders.repository.CashShiftRepository;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.OrderStatusRepository;
import com.centrral.centralres.features.orders.repository.PaymentMethodRepository;
import com.centrral.centralres.features.orders.repository.PaymentRepository;

class CashRegisterServiceTest {
    @Mock CashShiftRepository shifts;
    @Mock CashEntryRepository entries;
    @Mock UserRepository users;
    @Mock OrderRepository orders;
    @Mock OrderStatusRepository statuses;
    @Mock PaymentMethodRepository methods;
    @Mock PaymentRepository payments;
    @InjectMocks CashRegisterService service;

    User cashier;
    CashShift shift;

    private static BigDecimal money(String s) { return new BigDecimal(s); }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cashier = User.builder().id(10L).roles(Set.of(Role.builder().name("ROLE_CASHIER").build())).build();
        shift = new CashShift();
        shift.setId(7L);
        shift.setCashierUserId(10L);
        shift.setOpeningCash(money("100.00"));
        shift.setOpenedAt(Instant.now());
        when(shifts.lockCurrent(10L)).thenReturn(Optional.of(shift));
        when(entries.findByShift_IdOrderByCreatedAtAscIdAsc(7L)).thenReturn(List.of());
    }

    CashEntry entry(String type, String method, String amount) {
        CashEntry e = new CashEntry();
        e.setType(type);
        e.setMethodCode(method);
        e.setAmount(money(amount));
        e.setCreatedAt(Instant.now());
        return e;
    }

    @Test
    void cashierReconcilesOnlyPhysicalCashAndSeparatesElectronicReceipts() {
        when(entries.findByShift_IdOrderByCreatedAtAscIdAsc(7L)).thenReturn(List.of(
            entry("SALE", "CASH", "80.00"),
            entry("SALE", "YAPE", "120.00"),
            entry("CASH_IN", "CASH", "20.00"),
            entry("CASH_OUT", "CASH", "5.00")));

        var result = service.close(cashier, new CashRequests.Close(money("195.00"), null));

        assertThat(result.expectedCash()).isEqualByComparingTo("195.00");
        assertThat(result.difference()).isEqualByComparingTo("0.00");
        assertThat(result.salesByMethod().get("YAPE")).isEqualByComparingTo("120.00");
        assertThat(result.salesByMethod().get("CASH")).isEqualByComparingTo("80.00");
        assertThat(result.closedAt()).isNotNull();
        verify(shifts).save(shift);
    }

    @Test
    void differenceNeedsWrittenExplanation() {
        assertThatThrownBy(() -> service.close(cashier,
                new CashRequests.Close(money("90.00"), "")))
            .isInstanceOf(BadRequestException.class).hasMessageContaining("diferencia");
        verify(shifts, never()).save(any(CashShift.class));
    }

    @Test
    void recordsNegativeDifferenceWithReasonAndClosesShift() {
        var result = service.close(cashier,
                new CashRequests.Close(money("95.00"), "Faltante pendiente de revisión."));
        assertThat(result.difference()).isEqualByComparingTo("-5.00");
        assertThat(result.closingNote()).contains("revisión");
        verify(shifts).save(shift);
    }

    @Test
    void cannotCloseWhenARecordedPaymentIsNoLongerConfirmed() {
        CashEntry sale = entry("SALE", "CASH", "20.00");
        Payment reversed = Payment.builder().status(PaymentStatus.REFUNDED).build();
        sale.setPayment(reversed);
        when(entries.findByShift_IdOrderByCreatedAtAscIdAsc(7L)).thenReturn(List.of(sale));

        assertThatThrownBy(() -> service.close(cashier,
                new CashRequests.Close(money("120.00"), null)))
            .isInstanceOf(ConflictException.class);
        verify(shifts, never()).save(any(CashShift.class));
    }

    @Test
    void cannotCollectSameOrderTwice() {
        Order order = Order.builder().id(30L).type(OrderType.builder().code("DINE_IN").build())
            .status(OrderStatus.builder().code("CONFIRMED").build())
            .total(money("32.00")).payments(new LinkedHashSet<>()).build();
        when(orders.findByIdForUpdate(30L)).thenReturn(Optional.of(order));
        when(payments.existsByOrder_IdAndStatus(30L, PaymentStatus.CONFIRMED)).thenReturn(true);

        assertThatThrownBy(() -> service.collect(cashier, 30L, new CashRequests.Collect(1L, null)))
            .isInstanceOf(ConflictException.class).hasMessageContaining("cobrar dos veces");
        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void rejectsDeliveryCashCollection() {
        Order delivery = Order.builder().id(30L).type(OrderType.builder().code("DELIVERY").build()).build();
        when(orders.findByIdForUpdate(30L)).thenReturn(Optional.of(delivery));
        assertThatThrownBy(() -> service.collect(cashier, 30L, new CashRequests.Collect(1L, null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void onlyCashierRoleCanUseRegister() {
        User waiter = User.builder().id(20L)
            .roles(Set.of(Role.builder().name("ROLE_WAITER").build())).build();
        assertThatThrownBy(() -> service.current(waiter)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cannotWithdrawMoreCashThanExpected() {
        assertThatThrownBy(() -> service.move(cashier,
            new CashRequests.Movement("CASH_OUT", money("200.00"), "Retiro de caja")))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cashCollectionCreatesConfirmedPaymentAndAuditedEntry() {
        Order order = Order.builder().id(30L)
                .type(OrderType.builder().code("DINE_IN").build())
                .status(OrderStatus.builder().code("PENDING_CONFIRMATION").build())
                .total(money("32.00"))
                .payments(new LinkedHashSet<>()).build();
        when(orders.findByIdForUpdate(30L)).thenReturn(Optional.of(order));
        PaymentMethod method = PaymentMethod.builder().id(1L).code("CASH").provider("INTERNAL").build();
        when(methods.findById(1L)).thenReturn(Optional.of(method));
        when(payments.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(77L);
            return payment;
        });
        when(statuses.findByCode("CONFIRMED"))
                .thenReturn(Optional.of(OrderStatus.builder().code("CONFIRMED").build()));

        service.collect(cashier, 30L, new CashRequests.Collect(1L, null));

        org.mockito.ArgumentCaptor<Payment> pay = org.mockito.ArgumentCaptor.forClass(Payment.class);
        org.mockito.ArgumentCaptor<CashEntry> sale = org.mockito.ArgumentCaptor.forClass(CashEntry.class);
        verify(payments).save(pay.capture());
        verify(entries).save(sale.capture());
        assertThat(pay.getValue().getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(pay.getValue().getAmount()).isEqualByComparingTo("32.00");
        assertThat(sale.getValue().getOrderId()).isEqualTo(30L);
        assertThat(sale.getValue().getAmount()).isEqualByComparingTo("32.00");
        assertThat(order.getStatus().getCode()).isEqualTo("CONFIRMED");
    }
}
