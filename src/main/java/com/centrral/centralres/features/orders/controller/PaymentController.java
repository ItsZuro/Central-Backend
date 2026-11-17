package com.centrral.centralres.features.orders.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import com.centrral.centralres.features.orders.dto.niubiz.NiubizDTO;
import com.centrral.centralres.features.orders.dto.payment.request.PaymentStartRequest;
import com.centrral.centralres.features.orders.dto.payment.request.PaymentUpdateRequest;
import com.centrral.centralres.features.orders.dto.payment.response.PaymentResponse;
import com.centrral.centralres.features.orders.service.PaymentService;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

        private final PaymentService paymentService;

        @Value("${app.frontend.url}")
        private String frontendUrl;

        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
        @GetMapping
        public ResponseEntity<ApiResponse<List<PaymentResponse>>> getAll(
                        @RequestParam(required = false) Long customerId,
                        @RequestParam(required = false) Long paymentMethodId,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
                List<PaymentResponse> payments = paymentService.getAll(
                                customerId, paymentMethodId, dateFrom, dateTo);
                return ResponseEntity.ok(
                                new ApiResponse<>(true, "Lista de pagos", payments));
        }

        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
        @GetMapping("/{id}")
        public ResponseEntity<ApiResponse<PaymentResponse>> getById(@PathVariable Long id) {
                return ResponseEntity.ok(
                                new ApiResponse<>(true, "Pago encontrado", paymentService.getById(id)));
        }

        @PreAuthorize("isAuthenticated()")
        @PostMapping("/niubiz/start")
        public ResponseEntity<ApiResponse<NiubizDTO.PaymentStartResponse>> startNiubizPayment(
                        @RequestBody @Valid PaymentStartRequest request) {

                NiubizDTO.PaymentStartResponse response = paymentService.startOnlinePayment(request);
                return ResponseEntity.ok(new ApiResponse<>(true, "Sesión de Niubiz iniciada", response));
        }

        @PreAuthorize("isAuthenticated()")
        @PostMapping("/niubiz/confirm")
        public ResponseEntity<ApiResponse<PaymentResponse>> confirmNiubizPayment(
                        @RequestBody @Valid NiubizDTO.PaymentConfirmRequest request) {

                PaymentResponse response = paymentService.confirmOnlinePayment(request);
                return ResponseEntity.ok(new ApiResponse<>(true, "Pago con Niubiz confirmado", response));
        }

        @PostMapping(value = "/niubiz/callback", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
        public void niubizCallback(@RequestParam Map<String, String> body, HttpServletResponse response)
                        throws IOException {

                String transactionToken = body.get("transactionToken");

                String redirectUrl = frontendUrl + "/payment/validate?token=" + transactionToken;

                response.sendRedirect(redirectUrl);
        }

        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
        @PutMapping("/{id}")
        public ResponseEntity<ApiResponse<PaymentResponse>> update(
                        @PathVariable Long id,
                        @RequestBody @Valid PaymentUpdateRequest request) {
                PaymentResponse response = paymentService.update(id, request);
                return ResponseEntity.ok(
                                new ApiResponse<>(true, "Pago actualizado", response));
        }

        @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CASHIER')")
        @DeleteMapping("/{id}")
        public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
                paymentService.delete(id);
                return ResponseEntity.ok(
                                new ApiResponse<>(true, "Pago cancelado", null));
        }
}