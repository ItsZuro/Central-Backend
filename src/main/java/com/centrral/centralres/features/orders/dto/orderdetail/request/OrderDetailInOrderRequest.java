package com.centrral.centralres.features.orders.dto.orderdetail.request;

import java.util.List;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderDetailInOrderRequest {

    @NotNull(message = "El producto es obligatorio")
    private Long productId;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad debe ser mayor a 0")
    private Integer quantity;

    private List<Long> extraIds;

    @Size(max = 1000)
    private String notes;

}
