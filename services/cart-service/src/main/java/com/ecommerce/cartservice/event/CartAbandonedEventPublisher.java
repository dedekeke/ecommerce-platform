package com.ecommerce.cartservice.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link CartAbandonedEvent} messages to the {@code cart.abandoned}
 * topic. Failures are logged but not propagated — the scheduler must continue
 * processing the rest of the batch even if the broker is briefly unavailable.
 */
@Component
@Slf4j
public class CartAbandonedEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired(required = false)
    public CartAbandonedEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(CartAbandonedEvent event) {
        if (kafkaTemplate == null) {
            log.debug("KafkaTemplate not configured; skipping cart.abandoned publish for cartId={}",
                    event.getCartId());
            return;
        }
        try {
            kafkaTemplate.send(CartAbandonedEvent.TOPIC, event.getCartId(), event);
            log.info("Published cart.abandoned event for cartId={} userId={}",
                    event.getCartId(), event.getUserId());
        } catch (Exception ex) {
            log.error("Failed to publish cart.abandoned event for cartId={}",
                    event.getCartId(), ex);
        }
    }
}
