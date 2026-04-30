package com.ecommerce.orderservice.saga.refund;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RefundEventPublisher {

    public static final String REFUND_COMPLETED_TOPIC = "refund.completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRefundCompleted(RefundCompletedEvent event) {
        try {
            kafkaTemplate.send(REFUND_COMPLETED_TOPIC, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for order {}",
                            REFUND_COMPLETED_TOPIC, event.getOrderId(), ex);
                    } else {
                        log.info("Published {} for order {} (saga {})",
                            REFUND_COMPLETED_TOPIC, event.getOrderId(), event.getSagaId());
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing refund.completed event", e);
        }
    }
}
