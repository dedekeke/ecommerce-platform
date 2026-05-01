package com.ecommerce.orderservice.saga.rma;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Best-effort Kafka producer for the four RMA topics. Failures are
 * logged but never propagated — the saga must not roll back because a
 * notification could not be sent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RmaEventPublisher {

    public static final String RMA_REQUESTED_TOPIC = "rma.requested";
    public static final String RMA_RECEIVED_TOPIC = "rma.received";
    public static final String RMA_COMPLETED_TOPIC = "rma.completed";
    public static final String RMA_REJECTED_TOPIC = "rma.rejected";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishRequested(RmaEvent event) {
        send(RMA_REQUESTED_TOPIC, event);
    }

    public void publishReceived(RmaEvent event) {
        send(RMA_RECEIVED_TOPIC, event);
    }

    public void publishCompleted(RmaEvent event) {
        send(RMA_COMPLETED_TOPIC, event);
    }

    public void publishRejected(RmaEvent event) {
        send(RMA_REJECTED_TOPIC, event);
    }

    private void send(String topic, RmaEvent event) {
        try {
            kafkaTemplate.send(topic, event.getOrderId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} for RMA {}", topic, event.getRmaNumber(), ex);
                    } else {
                        log.info("Published {} for RMA {} (order {})",
                            topic, event.getRmaNumber(), event.getOrderId());
                    }
                });
        } catch (Exception e) {
            log.error("Error publishing {} event for RMA {}", topic, event.getRmaNumber(), e);
        }
    }
}
