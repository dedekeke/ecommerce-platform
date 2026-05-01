package com.ecommerce.reviewservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Product review.
 *
 * <p>Indexed by {@code productId} for the public read path
 * (PDP "All Reviews" tab) and by {@code (productId, helpful desc)} so the
 * default "most helpful" sort can be served from a single IXSCAN. The
 * {@code helpfulVoters} set provides per-review dedup of helpful clicks
 * without a separate collection.
 */
@Document(collection = "reviews")
@CompoundIndexes({
        @CompoundIndex(name = "product_helpful_desc", def = "{'productId': 1, 'helpful': -1}"),
        @CompoundIndex(name = "product_created_desc", def = "{'productId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "product_rating_desc",  def = "{'productId': 1, 'rating': -1}"),
        @CompoundIndex(name = "product_user_unique",  def = "{'productId': 1, 'userId': 1}", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewDocument {

    @Id
    private String id;

    private String productId;
    private String userId;

    /** 1..5 inclusive — enforced by the controller via @Min/@Max. */
    private int rating;

    private String title;
    private String body;

    /**
     * True if at the time the review was created, the user had a completed
     * order containing the productId. Falls back to false when order-service
     * is unreachable (see {@code OrderServiceClient}).
     */
    private boolean verified;

    /** Count of "found this helpful" clicks. */
    private long helpful;

    /** Set of userIds that already voted helpful — provides per-review dedup. */
    @Builder.Default
    private Set<String> helpfulVoters = new HashSet<>();

    private Instant createdAt;
}
