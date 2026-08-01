package com.ecommerce.promotionservice.saga.replenishment;

import com.ecommerce.promotionservice.model.Promotion;
import com.ecommerce.promotionservice.repository.PromotionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Choreography participant — the promotion side.
 *
 * <p>Consumes {@code stock.low.detected} and pauses any active promotion
 * targeting that product. Consumes {@code stock.replenished} and resumes
 * what it paused. Each path emits its own outcome event so audit / analytics
 * consumers can plug in without a code change here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PromotionStockListener {

    static final String TOPIC_LOW = "stock.low.detected";
    static final String TOPIC_REPLENISHED = "stock.replenished";
    static final String TOPIC_PAUSED = "promotion.paused.due-to-stock";
    static final String TOPIC_RESUMED = "promotion.resumed.due-to-stock";

    private final PromotionRepository promotionRepository;
    private final ConsumedReplenishmentEventRepository consumedRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = TOPIC_LOW, groupId = "promotion-service-replenishment")
    @Transactional
    public void onStockLow(String message) {
        try {
            StockLowDetectedEvent event = objectMapper.readValue(message, StockLowDetectedEvent.class);
            if (alreadyConsumed(event.eventId(), TOPIC_LOW)) {
                log.warn("Skipping duplicate {} eventId={}", TOPIC_LOW, event.eventId());
                return;
            }

            List<Promotion> toPause = findActivePromotionsForProduct(event.productId());
            toPause.forEach(p -> p.setActive(false));
            promotionRepository.saveAll(toPause);
            recordConsumed(event.eventId(), TOPIC_LOW);

            kafkaTemplate.send(TOPIC_PAUSED, String.valueOf(event.productId()),
                    new PromotionPausedEvent(UUID.randomUUID(), event.eventId(),
                            event.productId(), toPause.size(), Instant.now()));
            log.info("Paused {} promotion(s) for productId={} due to stock.low.detected",
                    toPause.size(), event.productId());
        } catch (Exception e) {
            log.error("Failed to process {} event", TOPIC_LOW, e);
        }
    }

    @KafkaListener(topics = TOPIC_REPLENISHED, groupId = "promotion-service-replenishment")
    @Transactional
    public void onStockReplenished(String message) {
        try {
            StockReplenishedEvent event = objectMapper.readValue(message, StockReplenishedEvent.class);
            if (alreadyConsumed(event.eventId(), TOPIC_REPLENISHED)) {
                log.warn("Skipping duplicate {} eventId={}", TOPIC_REPLENISHED, event.eventId());
                return;
            }

            List<Promotion> toResume = findPausedPromotionsForProduct(event.productId());
            toResume.forEach(p -> p.setActive(true));
            promotionRepository.saveAll(toResume);
            recordConsumed(event.eventId(), TOPIC_REPLENISHED);

            kafkaTemplate.send(TOPIC_RESUMED, String.valueOf(event.productId()),
                    new PromotionResumedEvent(UUID.randomUUID(), event.eventId(),
                            event.productId(), toResume.size(), Instant.now()));
            log.info("Resumed {} promotion(s) for productId={} due to stock.replenished",
                    toResume.size(), event.productId());
        } catch (Exception e) {
            log.error("Failed to process {} event", TOPIC_REPLENISHED, e);
        }
    }

    private boolean alreadyConsumed(UUID eventId, String topic) {
        if (eventId == null) {
            return false;
        }
        return consumedRepository.existsById(eventId);
    }

    private void recordConsumed(UUID eventId, String topic) {
        if (eventId == null) {
            return;
        }
        consumedRepository.save(ConsumedReplenishmentEvent.builder()
                .eventId(eventId)
                .topic(topic)
                .consumedAt(Instant.now())
                .build());
    }

    /**
     * Active promotions linked to the given product via the
     * {@code promotion_products} join table.
     */
    List<Promotion> findActivePromotionsForProduct(Long productId) {
        return promotionRepository.findActiveByProductId(productId);
    }

    /** Promotions previously paused for this product, ready to resume. */
    List<Promotion> findPausedPromotionsForProduct(Long productId) {
        return promotionRepository.findInactiveByProductId(productId);
    }
}
