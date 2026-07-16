package com.ecommerce.orderservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/orders/{orderId}/mark-shipped}: the carrier and
 * tracking number an operator (or a future carrier integration) supplies when
 * the order ships.
 */
public record MarkShippedRequest(
    @NotBlank(message = "Carrier is required")
    @Size(max = 100, message = "Carrier must be at most 100 characters")
    String carrier,

    @NotBlank(message = "Tracking number is required")
    @Size(max = 100, message = "Tracking number must be at most 100 characters")
    String trackingNumber
) {}
