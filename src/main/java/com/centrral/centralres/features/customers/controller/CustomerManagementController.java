package com.centrral.centralres.features.customers.controller;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.centrral.centralres.core.dto.response.PagedResponse;
import com.centrral.centralres.core.security.dto.ApiResponse;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.features.customers.dto.customer.request.CustomerAccountStatusRequest;
import com.centrral.centralres.features.customers.dto.customer.response.CustomerAccountActionResponse;
import com.centrral.centralres.features.customers.dto.customer.response.CustomerManagementSummary;
import com.centrral.centralres.features.customers.dto.customer.response.CustomerResponse;
import com.centrral.centralres.features.customers.dto.reservation.response.ReservationResponse;
import com.centrral.centralres.features.customers.service.CustomerManagementService;
import com.centrral.centralres.features.customers.service.ReservationService;
import com.centrral.centralres.features.orders.dto.order.response.OrderResponse;
import com.centrral.centralres.features.orders.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/customer-management")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@RequiredArgsConstructor
public class CustomerManagementController {
    private final CustomerManagementService customerManagementService;
    private final OrderService orderService;
    private final ReservationService reservationService;

    private Pageable paging(int page, int size, String... sortFields) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 50)),
                Sort.by(Sort.Direction.DESC, sortFields));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<CustomerResponse>>> directory(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Directorio de clientes",
                customerManagementService.findCustomers(query, enabled, paging(page, size, "id"))));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<CustomerManagementSummary>> summary() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Resumen de cuentas de clientes",
                customerManagementService.summary()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CustomerResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Ficha de cliente",
                customerManagementService.getCustomer(id)));
    }

    @GetMapping("/{id}/orders")
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> orders(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Pedidos del cliente",
                orderService.getCustomerOrdersForManagement(id, paging(page, size, "date", "id"), "es")));
    }

    @GetMapping("/{id}/reservations")
    public ResponseEntity<ApiResponse<PagedResponse<ReservationResponse>>> reservations(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size) {
        customerManagementService.getCustomer(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Reservas del cliente",
                reservationService.getReservationsByCustomer(
                        id, paging(page, size, "reservationDate", "reservationTime"))));
    }

    @GetMapping("/{id}/actions")
    public ResponseEntity<ApiResponse<List<CustomerAccountActionResponse>>> actions(@PathVariable Long id) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Historial de cambios de cuenta",
                customerManagementService.history(id)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<CustomerResponse>> status(
            @PathVariable Long id, @AuthenticationPrincipal User actor,
            @Valid @RequestBody CustomerAccountStatusRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true,
                request.getEnabled() ? "Cuenta de cliente reactivada" : "Cuenta de cliente suspendida",
                customerManagementService.changeStatus(id, request, actor)));
    }
}
