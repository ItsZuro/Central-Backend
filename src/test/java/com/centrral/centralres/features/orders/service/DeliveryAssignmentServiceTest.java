package com.centrral.centralres.features.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.MockitoAnnotations;

import com.centrral.centralres.core.exceptions.ConflictException;
import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.features.employees.enums.EmploymentStatus;
import com.centrral.centralres.features.employees.model.Employee;
import com.centrral.centralres.features.employees.repository.EmployeeRepository;
import com.centrral.centralres.features.orders.dto.order.request.AssignDriverRequest;
import com.centrral.centralres.features.orders.dto.order.request.UpdateStatusRequest;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.model.OrderType;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.OrderStatusRepository;

class DeliveryAssignmentServiceTest {
    @Mock OrderRepository orders;
    @Mock EmployeeRepository employees;
    @Mock OrderStatusRepository statuses;
    @InjectMocks OrderService service;

    Order order;
    Employee driver;

    private User user(Long id, boolean enabled, String role) {
        return User.builder().id(id).username("staff" + id)
                .firstName("Luis").lastName("Mendoza").enabled(enabled)
                .roles(Set.of(Role.builder().name(role).build())).build();
    }
    private Employee employee(Long id, User user, EmploymentStatus status) {
        Employee e = Employee.builder().user(user).status(status).build();
        e.setId(id);
        return e;
    }
    private OrderStatus status(String code) {
        return OrderStatus.builder().id(5L).code(code)
                .translations(new LinkedHashSet<>()).build();
    }
    @BeforeEach
    void init() {
        MockitoAnnotations.openMocks(this);
        driver = employee(22L, user(15L, true, "ROLE_DRIVER"), EmploymentStatus.ACTIVE);
        order = Order.builder().id(4L).date(LocalDateTime.of(2026,11,22,17,0))
                .status(status("READY_FOR_PICKUP"))
                .type(OrderType.builder().id(2L).code("DELIVERY")
                        .translations(new LinkedHashSet<>()).build())
                .total(new BigDecimal("45.00"))
                .details(new LinkedHashSet<>()).payments(new LinkedHashSet<>()).build();
        when(orders.findByIdForUpdate(4L)).thenReturn(Optional.of(order));
        when(orders.save(order)).thenReturn(order);
        when(employees.findById(22L)).thenReturn(Optional.of(driver));
    }
    private AssignDriverRequest assign(Long employeeId) {
        AssignDriverRequest req = new AssignDriverRequest();
        req.setEmployeeId(employeeId);
        return req;
    }
    @Test
    void oldInRouteOrderWithoutEmployeeCanBeRecoveredWithoutStatusChange() {
        order.setStatus(status("OUT_FOR_DELIVERY"));
        var response = service.assignDriver(4L, assign(22L), "es");

        assertThat(response.getEmployeeId()).isEqualTo(22L);
        assertThat(response.getEmployeeName()).isEqualTo("Luis Mendoza");
        assertThat(response.getStatusCode()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(order.getEmployee()).isSameAs(driver);
        verify(orders).save(order);
    }
    @Test
    void existingInRouteAssignmentCannotBeOverwritten() {
        order.setStatus(status("OUT_FOR_DELIVERY"));
        order.setEmployee(driver);
        assertThatThrownBy(() -> service.assignDriver(4L, assign(22L), "es"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ya está en ruta");
        verify(orders, never()).save(any(Order.class));
    }
    @Test
    void activeDriverCanBeAssignedBeforeRouteWithoutDispatching() {
        var response = service.assignDriver(4L, assign(22L), "es");
        assertThat(response.getEmployeeId()).isEqualTo(22L);
        assertThat(response.getStatusCode()).isEqualTo("READY_FOR_PICKUP");
    }
    @Test
    void disabledDriverCannotBeAssigned() {
        driver.getUser().setEnabled(false);
        assertThatThrownBy(() -> service.assignDriver(4L, assign(22L), "es"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("cuenta habilitada");
    }
    @Test
    void cashierCannotBeAssignedAsDriver() {
        driver.getUser().setRoles(Set.of(Role.builder().name("ROLE_CASHIER").build()));
        assertThatThrownBy(() -> service.assignDriver(4L, assign(22L), "es"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("reparto");
    }
    @Test
    void generalOrderStatusCannotDispatchDeliveriesEvenIfAssigned() {
        order.setEmployee(driver);
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatusCode("OUT_FOR_DELIVERY");
        when(statuses.findByCode("OUT_FOR_DELIVERY")).thenReturn(Optional.of(status("OUT_FOR_DELIVERY")));
        assertThatThrownBy(() -> service.updateStatus(4L, request, "es"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("repartidor asignado");
        verify(orders, never()).save(any(Order.class));
    }
    @Test
    void availableDriversAreOnlyActiveEnabledDriverAccounts() {
        var active = driver;
        var disabled = employee(23L, user(16L, false, "ROLE_DRIVER"), EmploymentStatus.ACTIVE);
        var onLeave = employee(24L, user(17L, true, "ROLE_DRIVER"), EmploymentStatus.ON_LEAVE);
        var cashier = employee(25L, user(18L, true, "ROLE_CASHIER"), EmploymentStatus.ACTIVE);
        when(employees.findAll()).thenReturn(List.of(active, disabled, onLeave, cashier));

        var result = service.getEligibleDrivers();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).employeeId()).isEqualTo(22L);
        assertThat(result.get(0).fullName()).isEqualTo("Luis Mendoza");
    }
}
