package com.centrral.centralres.features.orders.dto.order.response;

import java.time.LocalDateTime;

/**
 * Oferta pública solo para repartidores habilitados.
 * No expone nombre del cliente, teléfono, calle exacta, referencias ni coordenadas.
 */
public record AvailableDeliveryResponse(
        Long orderId,
        LocalDateTime createdAt,
        String deliveryArea,
        int itemCount) {}
