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
 * <p>On {@code stock.low.detected} the product's {@code lowStockPenalty} flag
 * is set so the relevance query down-weights it. On {@code stock.replenished}
 * the flag is cleared. Each path emits its own outcome event.
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

    private void applyLowStockPenalty(Long productId, boolean penalize) {
        Optional<ProductDocument> doc = productSearchRepository.findById(String.valueOf(productId));
        doc.ifPresent(d -> {
            d.setLowStockPenalty(penalize ? Boolean.TRUE : Boolean.FALSE);
            productSearchRepository.save(d);
        });
    }
}
