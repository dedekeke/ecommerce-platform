package com.ecommerce.reviewservice.dto;

import java.io.Serializable;
import java.util.Map;

/**
 * Aggregate stats for a product's reviews.
 *
 * @param averageRating arithmetic mean of all ratings (0.0 when no reviews)
 * @param count         total number of reviews
 * @param distribution  histogram keyed by rating value 1..5
 */
public record ReviewSummaryResponse(
        double averageRating,
        long count,
        Map<Integer, Long> distribution
) implements Serializable {
}
