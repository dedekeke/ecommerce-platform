package com.ecommerce.promotionservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromotionValidationRequest {

    @NotBlank(message = "Promotion code is required")
    private String code;

    @NotNull(message = "Purchase amount is required")
    @DecimalMin(value = "0.0", message = "Purchase amount must be non-negative")
    private BigDecimal purchaseAmount;

    private Long categoryId;
}
