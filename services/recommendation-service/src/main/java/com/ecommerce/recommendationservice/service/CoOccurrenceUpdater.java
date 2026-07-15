package com.ecommerce.recommendationservice.service;

import com.ecommerce.recommendationservice.domain.CoOccurrenceDocument;
import com.ecommerce.recommendationservice.domain.ConsumedOrderDocument;
import com.ecommerce.recommendationservice.domain.UserPurchaseDocument;
import com.ecommerce.recommendationservice.repository.ConsumedOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Updates the co-occurrence matrix and per-user purchase set in response to an
 * ingested order.
 *
 * <p><b>Algorithm.</b> For an order with N <i>distinct</i> productIds, we
 * generate N*(N-1) directed pair updates (every (a, b) where a != b) and one
 * upsert into {@code user_purchases}. Each pair update is an upsert on the
 * composite key {@code "<productId>:<otherProductId>"} with a {@code $inc} on
 * {@code count} — atomic at the document level.
 *
 * <p><b>Idempotency.</b> Before doing any work, we attempt to insert a document
 * into {@code consumed_orders} keyed by orderId. The unique {@code _id}
 * constraint causes duplicate inserts to fail with
 * {@link DuplicateKeyException}, which we treat as "already processed" and
 * silently drop. This protects against Kafka at-least-once redeliveries and
 * topic replays.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoOccurrenceUpdater {

    private final MongoTemplate mongoTemplate;
    private final ConsumedOrderRepository consumedOrderRepository;

    /**
     * Ingests an order's products into the co-occurrence matrix and the user's
     * purchase set. Idempotent on orderId.
     *
     * @return true if work was performed, false if the order was already processed.
     */
    public boolean ingestOrder(String orderId, String userId, List<String> productIds) {
        if (orderId == null || orderId.isBlank()) {
            log.warn("Ignoring order with null/blank orderId");
            return false;
        }
        if (productIds == null || productIds.isEmpty()) {
            log.debug("Order {} has no products to ingest", orderId);
            return false;
        }

        if (!claimOrder(orderId)) {
            log.info("Order {} already ingested, skipping", orderId);
            return false;
        }

        // De-duplicate while preserving order so test expectations are deterministic.
        List<String> distinctProducts = new LinkedHashSet<>(productIds).stream().toList();

        if (userId != null && !userId.isBlank()) {
            upsertUserPurchases(userId, distinctProducts);
        }

        incrementPairs(distinctProducts);

        log.info("Ingested order {} with {} distinct products", orderId, distinctProducts.size());
        return true;
    }

    /**
     * Atomically claim ownership of this orderId. Returns false if another
     * delivery already claimed it.
     */
    private boolean claimOrder(String orderId) {
        try {
            consumedOrderRepository.insert(ConsumedOrderDocument.builder()
                    .orderId(orderId)
                    .consumedAt(Instant.now())
                    .build());
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /**
     * Atomically merge {@code productIds} into the user's purchase set.
     *
     * <p>Uses a single {@code $addToSet ... $each} upsert rather than a
     * findById → merge → save round-trip: the latter races on concurrent order
     * events for the same user (last write wins, silently dropping purchase
     * history). {@code $addToSet} is applied server-side and is set-safe, so
     * concurrent deliveries compose instead of clobbering each other.
     */
    private void upsertUserPurchases(String userId, List<String> productIds) {
        Query query = new Query(Criteria.where("_id").is(userId));
        Update update = new Update()
                .addToSet("productIds").each(productIds.toArray())
                .set("updatedAt", Instant.now());
        mongoTemplate.upsert(query, update, UserPurchaseDocument.class);
    }

    /**
     * For each ordered pair (a, b) with a != b, upsert a row keyed by
     * {@code "a:b"} and atomically increment {@code count} by 1.
     *
     * <p>Two directions are stored so per-product top-N reads stay a single
     * indexed query rather than needing a $or on either side of the pair.
     */
    private void incrementPairs(List<String> productIds) {
        Instant now = Instant.now();
        for (int i = 0; i < productIds.size(); i++) {
            for (int j = 0; j < productIds.size(); j++) {
                if (i == j) {
                    continue;
                }
                String a = productIds.get(i);
                String b = productIds.get(j);
                String id = CoOccurrenceDocument.buildId(a, b);

                Query query = new Query(Criteria.where("_id").is(id));
                Update update = new Update()
                        .inc("count", 1L)
                        .set("updatedAt", now)
                        .setOnInsert("productId", a)
                        .setOnInsert("otherProductId", b);

                mongoTemplate.upsert(query, update, CoOccurrenceDocument.class);
            }
        }
    }
}
