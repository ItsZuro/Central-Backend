package com.centrral.centralres.features.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import com.centrral.centralres.core.config.PostgresWebSocketBridge;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import com.centrral.centralres.core.exceptions.ConflictException;
import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.features.employees.enums.EmploymentStatus;
import com.centrral.centralres.features.employees.model.Employee;
import com.centrral.centralres.features.employees.repository.EmployeeRepository;
import com.centrral.centralres.features.orders.enums.PaymentStatus;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderDetail;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.model.OrderType;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.PaymentRepository;

class HybridDeliveryClaimServiceTest {
    @Mock OrderRepository orders;
    @Mock EmployeeRepository employees;
    @Mock PaymentRepository payments;
    @Mock PostgresWebSocketBridge wsEvents;
    @InjectMocks OrderService service;

    private User driverUser;
    private Employee driver;
    private Order order;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        driverUser = User.builder().id(11L).username("driver")
                .firstName("Luis").lastName("Mendoza").enabled(true)
                .roles(Set.of(Role.builder().name("ROLE_DRIVER").build())).build();
        driver = Employee.builder().user(driverUser).status(EmploymentStatus.ACTIVE).build();
        driver.setId(22L);
        order = Order.builder().id(50L)
                .type(OrderType.builder().id(2L).code("DELIVERY")
                        .translations(new LinkedHashSet<>()).build())
                .status(OrderStatus.builder().id(3L).code("READY_FOR_PICKUP")
                        .translations(new LinkedHashSet<>()).build())
                .date(LocalDateTime.of(2026, 11, 22, 19, 0))
                .deliveryStreet("Dirección privada del cliente")
                .deliveryCity("Ica").deliveryProvince("Ica")
                .total(new BigDecimal("45.00"))
                .details(new LinkedHashSet<>())
                .payments(new LinkedHashSet<>()).build();

        when(employees.findByUserId(11L)).thenReturn(Optional.of(driver));
        when(orders.findByIdForUpdate(50L)).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);
        when(payments.existsByOrder_IdAndStatus(50L, PaymentStatus.CONFIRMED)).thenReturn(true);
    }

    @Test
    void availableOffersRevealAreaButNotFullCustomerAddress() {
        OrderDetail item = OrderDetail.builder().id(70L).quantity(2).build();
        order.getDetails().add(item);
        when(orders.findAvailableDeliveryOffers(eq(PaymentStatus.CONFIRMED), any(Pageable.class)))
                .thenReturn(List.of(order));

        var offers = service.getAvailableDeliveries(driverUser);

        assertThat(offers).hasSize(1);
        assertThat(offers.get(0).orderId()).isEqualTo(50L);
        assertThat(offers.get(0).deliveryArea()).isEqualTo("Ica");
        assertThat(offers.get(0).itemCount()).isEqualTo(2);
        assertThat(offers.get(0).toString()).doesNotContain("Dirección privada");
    }

    @Test
    void driverCanClaimReadyPaidUnassignedDelivery() {
        var result = service.acceptAvailableDelivery(50L, driverUser, "es");

        assertThat(order.getEmployee()).isSameAs(driver);
        assertThat(result.getEmployeeId()).isEqualTo(22L);
        assertThat(result.getStatusCode()).isEqualTo("READY_FOR_PICKUP");
        verify(orders).findByIdForUpdate(50L);
        verify(orders).save(order);
    }

    @Test
    void secondClaimCannotOverwriteManagerOrAnotherDriverAssignment() {
        Employee managerAssigned = Employee.builder().status(EmploymentStatus.ACTIVE).build();
        managerAssigned.setId(77L);
        order.setEmployee(managerAssigned);

        assertThatThrownBy(() -> service.acceptAvailableDelivery(50L, driverUser, "es"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya aceptó");
        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void cannotClaimDeliveryBeforeCookingFinishes() {
        order.setStatus(OrderStatus.builder().code("IN_PROGRESS").build());

        assertThatThrownBy(() -> service.acceptAvailableDelivery(50L, driverUser, "es"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya no está listo");
        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void cannotClaimDeliveryWithoutConfirmedPayment() {
        when(payments.existsByOrder_IdAndStatus(50L, PaymentStatus.CONFIRMED)).thenReturn(false);

        assertThatThrownBy(() -> service.acceptAvailableDelivery(50L, driverUser, "es"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("pago confirmado");
        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void disabledOrInactiveDriversAreRejected() {
        driver.setStatus(EmploymentStatus.ON_LEAVE);
        assertThatThrownBy(() -> service.acceptAvailableDelivery(50L, driverUser, "es"))
                .isInstanceOf(AccessDeniedException.class);
        driver.setStatus(EmploymentStatus.ACTIVE);
        driverUser.setEnabled(false);
        assertThatThrownBy(() -> service.acceptAvailableDelivery(50L, driverUser, "es"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void otherRolesCannotViewOffersOrSelfAssign() {
        User cashier = User.builder().id(51L).enabled(true)
                .roles(Set.of(Role.builder().name("ROLE_CASHIER").build())).build();
        assertThatThrownBy(() -> service.getAvailableDeliveries(cashier))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.acceptAvailableDelivery(50L, cashier, "es"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
