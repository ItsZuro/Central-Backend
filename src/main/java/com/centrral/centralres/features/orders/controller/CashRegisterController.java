package com.centrral.centralres.features.orders.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.centrral.centralres.core.security.dto.ApiResponse;
import com.centrral.centralres.core.security.model.User;
import com.centrral.centralres.features.orders.dto.cash.CashRequests;
import com.centrral.centralres.features.orders.dto.cash.CashShiftResponse;
import com.centrral.centralres.features.orders.service.CashRegisterService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Un cajero solo consulta y modifica sus propias cajas. */
@RestController
@RequestMapping("/api/v1/cash-register")
@PreAuthorize("hasRole('CASHIER')")
@RequiredArgsConstructor
public class CashRegisterController {
    private final CashRegisterService service;

    @GetMapping("/current")
    public ResponseEntity<ApiResponse<CashShiftResponse>> current(@AuthenticationPrincipal User cashier) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Turno actual", service.current(cashier)));
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<CashShiftResponse>>> history(@AuthenticationPrincipal User cashier) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Cierres anteriores", service.history(cashier)));
    }

    @PostMapping("/open")
    public ResponseEntity<ApiResponse<CashShiftResponse>> open(
            @AuthenticationPrincipal User cashier, @Valid @RequestBody CashRequests.Open request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Turno de caja abierto",
                service.open(cashier, request)));
    }

    @PostMapping("/movement")
    public ResponseEntity<ApiResponse<CashShiftResponse>> movement(
            @AuthenticationPrincipal User cashier, @Valid @RequestBody CashRequests.Movement request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Movimiento de efectivo registrado",
                service.move(cashier, request)));
    }

    @PostMapping("/orders/{orderId}/collect")
    public ResponseEntity<ApiResponse<CashShiftResponse>> collect(
            @PathVariable Long orderId, @AuthenticationPrincipal User cashier,
            @Valid @RequestBody CashRequests.Collect request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Pago confirmado y registrado en caja",
                service.collect(cashier, orderId, request)));
    }

    @PostMapping("/close")
    public ResponseEntity<ApiResponse<CashShiftResponse>> close(
            @AuthenticationPrincipal User cashier, @Valid @RequestBody CashRequests.Close request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Turno de caja cerrado",
                service.close(cashier, request)));
    }
}
