package com.centrral.centralres.features.orders.dto.orderdetail.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderDetailInOrderResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String selectedExtras;
    private String notes;
}
