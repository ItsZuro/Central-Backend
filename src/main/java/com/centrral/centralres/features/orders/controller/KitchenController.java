package com.centrral.centralres.features.orders.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.centrral.centralres.core.security.dto.ApiResponse;
import com.centrral.centralres.features.orders.dto.order.request.UpdateStatusRequest;
import com.centrral.centralres.features.orders.dto.order.response.KitchenOrderResponse;
import com.centrral.centralres.features.orders.service.KitchenService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders/kitchen")
@PreAuthorize("hasRole('CHEF')")
@RequiredArgsConstructor
public class KitchenController {
    private final KitchenService kitchenService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<KitchenOrderResponse>>> board() {
        return ResponseEntity.ok(new ApiResponse<>(true, "Pedidos de cocina",
                kitchenService.board()));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<ApiResponse<KitchenOrderResponse>> advance(
            @PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(new ApiResponse<>(true, "Preparación actualizada",
                kitchenService.advance(id, request.getNewStatusCode())));
    }
}
