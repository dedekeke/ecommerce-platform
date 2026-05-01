package com.ecommerce.recommendationservice.dto;

import java.io.Serializable;

/**
 * Single recommended product entry returned to the API caller.
 *
 * <p>{@code score} is the raw co-occurrence count for product-based recs, or the
 * sum of co-occurrences across all of the user's owned products for user-based
 * recs. We expose it as a numeric "score" rather than "count" to keep room for
 * future weighting (recency decay, conversion rate, etc.) without an API break.
 *
 * <p>Implements {@link Serializable} so Spring Cache (with a serialising cache
 * manager such as Redis) can transparently cache lists of these.
 */
public record RecommendationResponse(String productId, long score) implements Serializable {
}
