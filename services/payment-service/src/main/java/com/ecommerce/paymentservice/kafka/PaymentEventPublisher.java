package com.ecommerce.paymentservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    public void publishPaymentCompletedEvent(PaymentEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, event.getOrderId(), eventJson);
            log.info("Published payment completed event for order: {}", event.getOrderId());
        } catch (JsonProcessingException e) {
            log.error("Error publishing payment completed event", e);
        }
    }

    public void publishPaymentFailedEvent(PaymentEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(PAYMENT_FAILED_TOPIC, event.getOrderId(), eventJson);
            log.info("Published payment failed event for order: {}", event.getOrderId());
        } catch (JsonProcessingException e) {
            log.error("Error publishing payment failed event", e);
        }
    }
}
