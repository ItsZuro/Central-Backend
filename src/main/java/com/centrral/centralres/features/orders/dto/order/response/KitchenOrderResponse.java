package com.centrral.centralres.features.orders.dto.order.response;

import java.time.OffsetDateTime;
import java.util.List;

/** Vista mínima para cocina: no expone dirección, pagos ni datos personales. */
public record KitchenOrderResponse(
        Long id,
        OffsetDateTime date,
        String statusCode,
        String typeCode,
        String tableCode,
        String pickupStoreName,
        boolean paymentConfirmed,
        List<KitchenLine> items) {

    public record KitchenLine(
            Long id,
            String productName,
            Integer quantity,
            String notes,
            String selectedExtras) {}
}
