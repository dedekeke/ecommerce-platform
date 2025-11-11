package com.ecommerce.productservice.model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Embeddable value object representing product dimensions.
 * All measurements are in centimeters and kilograms.
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Dimensions {

    @DecimalMin(value = "0.0", inclusive = false, message = "Length must be greater than 0")
    private BigDecimal length;

    @DecimalMin(value = "0.0", inclusive = false, message = "Width must be greater than 0")
    private BigDecimal width;

    @DecimalMin(value = "0.0", inclusive = false, message = "Height must be greater than 0")
    private BigDecimal height;

    @DecimalMin(value = "0.0", inclusive = false, message = "Weight must be greater than 0")
    private BigDecimal weight;
}
