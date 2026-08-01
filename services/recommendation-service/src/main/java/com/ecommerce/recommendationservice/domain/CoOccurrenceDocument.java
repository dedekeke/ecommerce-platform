package com.ecommerce.recommendationservice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Represents the directed co-occurrence count between two products.
 *
 * <p>For an order containing products A and B, two documents are upserted:
 * one with productId=A,otherProductId=B and another with productId=B,otherProductId=A.
 * Storing both directions keeps the per-product top-N query a simple
 * {@code productId == X} lookup, sorted by count desc.
 *
 * <p>The {@code _id} is a deterministic composite of the form
 * "<productId>:<otherProductId>" so re-processing the same pair is naturally
 * idempotent at the document level (combined with the orderId-level idempotency
 * tracked in {@link ConsumedOrderDocument}).
 */
@Document(collection = "co_occurrence")
@CompoundIndexes({
        @CompoundIndex(name = "product_count_desc", def = "{'productId': 1, 'count': -1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOccurrenceDocument {

    @Id
    private String id;

    private String productId;

    private String otherProductId;

    private long count;

    private Instant updatedAt;

    /**
     * Build the deterministic composite id used as the Mongo {@code _id}.
     */
    public static String buildId(String productId, String otherProductId) {
        return productId + ":" + otherProductId;
    }
}
