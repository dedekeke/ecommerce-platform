package com.ecommerce.searchservice.saga.replenishment;

import com.ecommerce.searchservice.document.ProductDocument;
import com.ecommerce.searchservice.repository.ProductSearchRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Choreography participant — the search side.
 *
 * <p>On {@code stock.low.detected} the product's relevance is reduced so it
 * stops climbing search results while it cannot be fulfilled. On
 * {@code stock.replenished} the boost is restored. Each path emits its own
 * outcome event.
 *
 * <p><strong>Stub note:</strong> the live wiring would set a
 * {@code lowStockPenalty} flag on the {@link ProductDocument} and rebuild
 * the score query in {@code ProductSearchService} to subtract that penalty
 * from the relevance. For this learning example we re-save the document
 * unchanged after fetching it — the call shape is honest, the search-side
 * scoring change is intentionally out of scope.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchStockListener {

    static final String TOPIC_LOW = "stock.low.detected";
    static final String TOPIC_REPLENISHED = "stock.replenished";
    static final String TOPIC_DEBOOSTED = "search.deboosted.due-to-stock";
    static final String TOPIC_REBOOSTED = "search.reboosted.due-to-stock";

    private final ProductSearchRepository productSearchRepository;
    private final ConsumedEventStore consumedStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_LOW, groupId = "search-service-replenishment")
    public void onStockLow(String message) {
        try {
            StockLowDetectedEvent event = objectMapper.readValue(message, StockLowDetectedEvent.class);
            if (!consumedStore.tryClaim(event.eventId())) {
                log.warn("Skipping duplicate {} eventId={}", TOPIC_LOW, event.eventId());
                return;
            }

            applyLowStockPenalty(event.productId(), true);
            kafkaTemplate.send(TOPIC_DEBOOSTED, String.valueOf(event.productId()),
                    new SearchDeboostedEvent(UUID.randomUUID(), event.eventId(),
                            event.productId(), Instant.now()));
            log.info("De-boosted productId={} due to stock.low.detected", event.productId());
        } catch (Exception e) {
            log.error("Failed to process {} event", TOPIC_LOW, e);
        }
    }

    @KafkaListener(topics = TOPIC_REPLENISHED, groupId = "search-service-replenishment")
    public void onStockReplenished(String message) {
        try {
            StockReplenishedEvent event = objectMapper.readValue(message, StockReplenishedEvent.class);
            if (!consumedStore.tryClaim(event.eventId())) {
                log.warn("Skipping duplicate {} eventId={}", TOPIC_REPLENISHED, event.eventId());
                return;
            }

            applyLowStockPenalty(event.productId(), false);
            kafkaTemplate.send(TOPIC_REBOOSTED, String.valueOf(event.productId()),
                    new SearchReboostedEvent(UUID.randomUUID(), event.eventId(),
                            event.productId(), Instant.now()));
            log.info("Re-boosted productId={} due to stock.replenished", event.productId());
        } catch (Exception e) {
            log.error("Failed to process {} event", TOPIC_REPLENISHED, e);
        }
    }

    /**
     * Stub: in production this would set a boolean flag on the document and
     * the relevance query would subtract a penalty when the flag is true.
     * Today we just re-save the doc to demonstrate the action ran.
     */
    private void applyLowStockPenalty(Long productId, boolean penalize) {
        Optional<ProductDocument> doc = productSearchRepository.findById(String.valueOf(productId));
        doc.ifPresent(productSearchRepository::save);
    }
}
