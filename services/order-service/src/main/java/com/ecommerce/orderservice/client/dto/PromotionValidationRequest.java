package com.ecommerce.orderservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionValidationRequest {

    private String code;
    private BigDecimal purchaseAmount;
    private Long categoryId;
}
