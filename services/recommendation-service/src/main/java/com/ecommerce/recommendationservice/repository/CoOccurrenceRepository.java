package com.ecommerce.recommendationservice.repository;

import com.ecommerce.recommendationservice.domain.CoOccurrenceDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CoOccurrenceRepository extends MongoRepository<CoOccurrenceDocument, String> {

    /**
     * Returns co-occurrence rows for a given product, sorted descending by count.
     * Backed by the {@code product_count_desc} compound index for an O(logN+limit)
     * scan instead of a full collection scan.
     */
    List<CoOccurrenceDocument> findByProductIdOrderByCountDesc(String productId, Pageable pageable);

    /**
     * Bulk fetch for personalised recommendations: aggregate co-occurrences across
     * all the user's owned products in a single query.
     */
    List<CoOccurrenceDocument> findByProductIdIn(List<String> productIds);
}
