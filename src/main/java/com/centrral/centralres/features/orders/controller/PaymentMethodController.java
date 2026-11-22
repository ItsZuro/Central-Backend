package com.centrral.centralres.features.orders.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.centrral.centralres.core.security.dto.ApiResponse;
import com.centrral.centralres.features.orders.dto.paymentmethod.request.PaymentMethodRequest;
import com.centrral.centralres.features.orders.dto.paymentmethod.resposne.PaymentMethodResponse;
import com.centrral.centralres.features.orders.service.PaymentMethodService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payment-methods")
@RequiredArgsConstructor
public class PaymentMethodController {

    private final PaymentMethodService paymentMethodService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentMethodResponse>>> getAll(
            @RequestParam(value = "lang", required = false, defaultValue = "es") String lang) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Lista de métodos de pago", paymentMethodService.getAll(lang)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> getById(
            @PathVariable Long id,
            @RequestParam(value = "lang", required = false, defaultValue = "es") String lang) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Método de pago encontrado", paymentMethodService.getById(id, lang)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> create(
            @Valid @RequestBody PaymentMethodRequest request) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Método de pago creado", paymentMethodService.create(request)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaymentMethodResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody PaymentMethodRequest request) {
        return ResponseEntity.ok(
                new ApiResponse<>(true, "Método de pago actualizado", paymentMethodService.update(id, request)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        paymentMethodService.delete(id);
        return ResponseEntity.ok(new ApiResponse<>(true, "Método de pago eliminado", null));
    }

}
