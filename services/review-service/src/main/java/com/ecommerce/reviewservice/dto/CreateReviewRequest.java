package com.ecommerce.reviewservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Inbound payload for {@code POST /api/reviews}.
 *
 * <p>The userId is taken from the JWT, never the request body, so a malicious
 * caller cannot post reviews on behalf of someone else.
 */
public record CreateReviewRequest(
        @NotBlank String productId,
        @Min(1) @Max(5) int rating,
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String body
) {
}
