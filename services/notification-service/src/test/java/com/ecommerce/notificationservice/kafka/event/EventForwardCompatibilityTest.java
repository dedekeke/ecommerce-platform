package com.ecommerce.notificationservice.kafka.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Forward-compatibility guard: every consumer-side event DTO must tolerate
 * unknown fields, so a producer adding a field does not break deserialization
 * in notification-service.
 */
class EventForwardCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void should_deserializeOrderEvent_when_producerAddsUnknownField() throws Exception {
        String json = """
                {
                  "orderId": "order-1",
                  "orderNumber": "ORD-1",
                  "userEmail": "user@example.com",
                  "brandNewProducerField": "ignored"
                }
                """;

        OrderEvent event = objectMapper.readValue(json, OrderEvent.class);

        assertThat(event.getOrderId()).isEqualTo("order-1");
        assertThat(event.getUserEmail()).isEqualTo("user@example.com");
    }

    @Test
    void should_deserializePromotionEvent_when_producerAddsUnknownField() throws Exception {
        String json = """
                {
                  "promoCode": "SAVE10",
                  "name": "Spring Sale",
                  "userEmail": "user@example.com",
                  "brandNewProducerField": "ignored"
                }
                """;

        PromotionEvent event = objectMapper.readValue(json, PromotionEvent.class);

        assertThat(event.getPromoCode()).isEqualTo("SAVE10");
        assertThat(event.getName()).isEqualTo("Spring Sale");
    }

    @Test
    void should_notThrow_forAllConsumerEventDtos_when_unknownFieldPresent() {
        String extra = "{\"unknownProducerField\": \"x\"}";

        assertThatCode(() -> {
            objectMapper.readValue(extra, OrderEvent.class);
            objectMapper.readValue(extra, PromotionEvent.class);
            objectMapper.readValue(extra, RefundCompletedEvent.class);
            objectMapper.readValue(extra, RmaEvent.class);
            objectMapper.readValue(extra, CartAbandonedEvent.class);
        }).doesNotThrowAnyException();
    }
}
