package com.ecommerce.reviewservice.dto;

import com.ecommerce.reviewservice.domain.ReviewDocument;

import java.io.Serializable;
import java.time.Instant;

/**
 * Read-only projection of {@link ReviewDocument} returned to the client.
 *
 * <p>Excludes the {@code helpfulVoters} set so we don't leak the full list of
 * userIds that voted helpful — only the aggregate count.
 */
public record ReviewResponse(
        String id,
        String productId,
        String userId,
        int rating,
        String title,
        String body,
        boolean verified,
        long helpful,
        Instant createdAt
) implements Serializable {

    public static ReviewResponse from(ReviewDocument doc) {
        return new ReviewResponse(
                doc.getId(),
                doc.getProductId(),
                doc.getUserId(),
                doc.getRating(),
                doc.getTitle(),
                doc.getBody(),
                doc.isVerified(),
                doc.getHelpful(),
                doc.getCreatedAt()
        );
    }
}
