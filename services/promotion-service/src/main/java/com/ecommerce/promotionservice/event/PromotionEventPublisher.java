package com.ecommerce.promotionservice.event;

import com.ecommerce.promotionservice.model.Promotion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Publishes promotion lifecycle events to Kafka.
 * Failures are logged but never propagated — the API call must succeed even
 * if the broker is unreachable.
 */
@Slf4j
@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired(required = false))
public class PromotionEventPublisher {

    public static final String PROMOTION_CREATED_TOPIC = "promotion.created";

    private final KafkaTemplate<String, PromotionEvent> kafkaTemplate;

    public void publishPromotionCreated(Promotion promotion) {
        if (kafkaTemplate == null) {
            log.debug("KafkaTemplate not configured; skipping promotion.created publish for {}",
                    promotion.getCode());
            return;
        }

        PromotionEvent event = PromotionEvent.builder()
                .eventType("PROMOTION_CREATED")
                .timestamp(LocalDateTime.now())
                .promoCode(promotion.getCode())
                .name(promotion.getName())
                .description(promotion.getDescription())
                .expiresAt(promotion.getEndDate())
                .build();

        try {
            kafkaTemplate.send(PROMOTION_CREATED_TOPIC, promotion.getCode(), event);
            log.info("Published PROMOTION_CREATED event for code: {}", promotion.getCode());
        } catch (Exception e) {
            log.error("Failed to publish PROMOTION_CREATED for code: {}", promotion.getCode(), e);
        }
    }
}
