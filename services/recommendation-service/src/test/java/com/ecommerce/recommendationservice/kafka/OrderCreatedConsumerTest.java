package com.ecommerce.recommendationservice.kafka;

import com.ecommerce.recommendationservice.service.CoOccurrenceUpdater;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCreatedConsumerTest {

    @Mock
    private CoOccurrenceUpdater coOccurrenceUpdater;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderCreatedConsumer consumer;

    private String validPayload;

    @BeforeEach
    void setUp() {
        validPayload = """
                {
                  "orderId": "ord-1",
                  "userId": "user-1",
                  "items": [
                    {"productId": "p1", "qty": 1},
                    {"productId": "p2", "qty": 2},
                    {"productId": "p3", "qty": 1}
                  ]
                }
                """;
    }

    @Test
    void should_deserialize_payload_and_forward_productIds_to_updater() {
        consumer.handleOrderCreated(validPayload);

        ArgumentCaptor<List<String>> productIdsCaptor = ArgumentCaptor.forClass(List.class);
        verify(coOccurrenceUpdater).ingestOrder(eq("ord-1"), eq("user-1"), productIdsCaptor.capture());
        assertThat(productIdsCaptor.getValue()).containsExactly("p1", "p2", "p3");
    }

    @Test
    void should_tolerate_unknown_extra_fields_in_payload() {
        String payloadWithExtras = """
                {
                  "orderId": "ord-2",
                  "userId": "user-2",
                  "totalAmount": 99.99,
                  "shippingAddress": "ignored",
                  "items": [
                    {"productId": "p1", "qty": 1, "name": "ignored"}
                  ]
                }
                """;

        consumer.handleOrderCreated(payloadWithExtras);

        verify(coOccurrenceUpdater).ingestOrder(eq("ord-2"), eq("user-2"), eq(List.of("p1")));
    }

    @Test
    void should_swallow_invalid_json_gracefully() {
        consumer.handleOrderCreated("not-json");

        verify(coOccurrenceUpdater, never()).ingestOrder(anyString(), anyString(), anyList());
    }

    @Test
    void should_skip_when_items_array_is_missing() throws Exception {
        String payload = """
                {"orderId": "ord-3", "userId": "user-3"}
                """;

        consumer.handleOrderCreated(payload);

        verify(coOccurrenceUpdater).ingestOrder(eq("ord-3"), eq("user-3"), eq(List.of()));
    }

    @Test
    void should_filter_out_null_or_blank_productIds() {
        String payload = """
                {
                  "orderId": "ord-4",
                  "userId": "user-4",
                  "items": [
                    {"productId": "p1", "qty": 1},
                    {"productId": null, "qty": 1},
                    {"productId": "", "qty": 1},
                    {"productId": "p2", "qty": 1}
                  ]
                }
                """;

        consumer.handleOrderCreated(payload);

        verify(coOccurrenceUpdater).ingestOrder(eq("ord-4"), eq("user-4"), eq(List.of("p1", "p2")));
    }

    @Test
    void should_continue_when_updater_throws() {
        when(coOccurrenceUpdater.ingestOrder(anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("mongo down"));

        // No exception should escape the listener.
        consumer.handleOrderCreated(validPayload);

        verify(coOccurrenceUpdater).ingestOrder(eq("ord-1"), eq("user-1"), any());
    }
}
