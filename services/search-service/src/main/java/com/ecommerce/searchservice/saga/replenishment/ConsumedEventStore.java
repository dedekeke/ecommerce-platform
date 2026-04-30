package com.ecommerce.searchservice.saga.replenishment;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency ledger for the search-service participant.
 *
 * <p>Pedagogy: search-service has no JPA datasource, only Elasticsearch.
 * For a learning example an in-memory set is the simplest honest answer
 * — it survives within a single JVM lifetime, which is enough to dedupe
 * Kafka at-least-once redeliveries. In production this should move to
 * Redis or to a small {@code consumed_event} ES index, with a TTL so it
 * does not grow forever.
 */
@Component
public class ConsumedEventStore {

    private final Set<UUID> seen = ConcurrentHashMap.newKeySet();

    public boolean tryClaim(UUID eventId) {
        if (eventId == null) {
            return true;
        }
        return seen.add(eventId);
    }
}
