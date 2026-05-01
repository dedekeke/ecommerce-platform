package com.ecommerce.gateway.graphql.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors {@code recommendation-service}'s {@code RecommendationResponse}
 * — productId + score. The BFF discards the score (the GraphQL schema
 * exposes only the resolved {@link ProductDto}) but we deserialize it
 * to keep the wire contract intact.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RecommendationDto(String productId, long score) {
}
