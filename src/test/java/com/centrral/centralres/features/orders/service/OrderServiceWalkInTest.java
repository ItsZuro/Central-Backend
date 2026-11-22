package com.centrral.centralres.features.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.centrral.centralres.core.config.TaxConfig;
import com.centrral.centralres.core.exceptions.BadRequestException;
import com.centrral.centralres.core.security.model.Role;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.features.customers.repository.CustomerRepository;
import com.centrral.centralres.features.employees.model.Employee;
import com.centrral.centralres.features.employees.repository.EmployeeRepository;
import com.centrral.centralres.features.invoices.service.InvoiceGenerator;
import com.centrral.centralres.features.orders.dto.order.request.OrderRequest;
import com.centrral.centralres.features.orders.dto.orderdetail.request.OrderDetailInOrderRequest;
import com.centrral.centralres.features.orders.dto.payment.request.PaymentInOrderRequest;
import com.centrral.centralres.features.orders.model.CashShift;
import com.centrral.centralres.features.orders.model.Order;
import com.centrral.centralres.features.orders.model.OrderStatus;
import com.centrral.centralres.features.orders.model.OrderType;
import com.centrral.centralres.features.orders.repository.CashShiftRepository;
import com.centrral.centralres.features.orders.repository.OrderRepository;
import com.centrral.centralres.features.orders.repository.OrderStatusRepository;
import com.centrral.centralres.features.orders.repository.OrderTypeRepository;
import com.centrral.centralres.features.orders.repository.PaymentMethodRepository;
import com.centrral.centralres.features.orders.repository.PaymentRepository;
import com.centrral.centralres.features.products.model.Product;
import com.centrral.centralres.features.products.repository.InventoryMovementRepository;
import com.centrral.centralres.features.products.repository.InventoryRepository;
import com.centrral.centralres.features.products.repository.ProductRepository;
import com.centrral.centralres.features.restaurant.model.Store;
import com.centrral.centralres.features.restaurant.repository.StoreRepository;
import com.centrral.centralres.features.restaurant.repository.TableRepository;

class OrderServiceWalkInTest {
    @Mock TaxConfig taxConfig;
    @Mock OrderRepository orderRepository;
    @Mock CustomerRepository customerRepository;
    @Mock EmployeeRepository employeeRepository;
    @Mock OrderStatusRepository statusRepository;
    @Mock OrderTypeRepository typeRepository;
    @Mock ProductRepository productRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock InventoryMovementRepository inventoryMovementRepository;
    @Mock DocumentService documentService;
    @Mock PaymentService paymentService;
    @Mock TableRepository tableRepository;
    @Mock StoreRepository storeRepository;
    @Mock DistanceMatrixService distanceMatrixService;
    @Mock DeliveryQuoteService deliveryQuoteService;
    @Mock InvoiceGenerator invoiceGenerator;
    @Mock PaymentMethodRepository paymentMethodRepository;
    @Mock CashShiftRepository cashShiftRepository;
    @Mock PaymentRepository paymentRepository;
    @InjectMocks OrderService service;

    User cashier;
    OrderRequest request;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        cashier = User.builder().id(17L).username("cajero")
                .roles(Set.of(Role.builder().name("ROLE_CASHIER").build())).build();

        request = new OrderRequest();
        request.setTypeId(2L);
        request.setStatusId(4L);
        request.setPickupStoreId(3L);
        OrderDetailInOrderRequest detail = new OrderDetailInOrderRequest();
        detail.setProductId(5L);
        detail.setQuantity(2);
        request.setDetails(List.of(detail));

        Employee employee = Employee.builder().user(cashier).build();
        employee.setId(8L);
        when(employeeRepository.findByUserId(17L)).thenReturn(Optional.of(employee));
        when(cashShiftRepository.findFirstByCashierUserIdAndClosedAtIsNull(17L))
                .thenReturn(Optional.of(new CashShift()));
        when(statusRepository.findById(4L)).thenReturn(Optional.of(
                OrderStatus.builder().id(4L).code("PENDING").build()));
        when(statusRepository.findByCode("PENDING_CONFIRMATION")).thenReturn(Optional.of(
                OrderStatus.builder().id(6L).code("PENDING_CONFIRMATION").build()));
        when(statusRepository.findById(6L)).thenReturn(Optional.of(
                OrderStatus.builder().id(6L).code("PENDING_CONFIRMATION").build()));
        when(typeRepository.findById(2L)).thenReturn(Optional.of(
                OrderType.builder().id(2L).code("TAKE_AWAY").build()));
        when(storeRepository.findById(3L)).thenReturn(Optional.of(
                Store.builder().id(3L).name("Central Ica").address("Ica").build()));
        when(productRepository.findById(5L)).thenReturn(Optional.of(
                Product.builder().id(5L).name("Jugo").price(new BigDecimal("5.00")).build()));
        when(taxConfig.getRate()).thenReturn(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(50L);
            return order;
        });
    }

    @Test
    void walkInTakeawayNeedsNoAccountOrTableAndLeavesPaymentPending() {
        var result = service.createPosSale(cashier, request, "es");

        assertThat(result.getId()).isEqualTo(50L);
        assertThat(result.getCustomerId()).isNull();
        assertThat(result.getCustomerName()).isEqualTo("Consumidor final");
        assertThat(result.getTableId()).isNull();
        assertThat(result.getPickupStoreId()).isEqualTo(3L);
        assertThat(result.getTotal()).isEqualByComparingTo("10.00");
        assertThat(result.getStatusCode()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(result.getPaymentStatus()).isEqualTo("PENDING");
        verifyNoInteractions(customerRepository);
        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getCustomer()).isNull();
    }

    @Test
    void browserCannotSubmitConfirmedPaymentsDirectlyThroughPos() {
        request.setPayments(List.of(new PaymentInOrderRequest()));
        assertThatThrownBy(() -> service.createPosSale(cashier, request, "es"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cobro desde caja");
    }

    @Test
    void browserCannotUsePosForDelivery() {
        when(typeRepository.findById(2L)).thenReturn(Optional.of(
                OrderType.builder().id(2L).code("DELIVERY").build()));
        assertThatThrownBy(() -> service.createPosSale(cashier, request, "es"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("salón o para llevar");
    }
}
