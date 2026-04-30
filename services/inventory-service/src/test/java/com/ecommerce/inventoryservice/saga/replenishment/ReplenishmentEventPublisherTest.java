package com.ecommerce.inventoryservice.saga.replenishment;

import com.ecommerce.inventoryservice.outbox.OutboxEvent;
import com.ecommerce.inventoryservice.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the post-outbox-migration {@link ReplenishmentEventPublisher}.
 *
 * <p>Contract:
 * <ul>
 *   <li>Both publish methods record an outbox row via {@link OutboxService}
 *       — they no longer call {@code KafkaTemplate.send} directly.</li>
 *   <li>The aggregate id surfaces the {@code productId} so consumers can
 *       partition and dedupe correctly.</li>
 *   <li>Outbox failures bubble up so the surrounding transaction rolls back.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReplenishmentEventPublisher — outbox-backed")
class ReplenishmentEventPublisherTest {

    @Mock
    private OutboxService outboxService;

    private ReplenishmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new ReplenishmentEventPublisher(outboxService);
    }

    @Test
    @DisplayName("should record stock.low.detected outbox row when publishing low-stock event")
    void should_recordStockLowOutboxRow_when_publishingStockLowDetected() {
        StockLowDetectedEvent event = new StockLowDetectedEvent(
                UUID.randomUUID(),
                100L,
                "SKU-100",
                2,
                5,
                Instant.now()
        );
        when(outboxService.recordEvent(any(), any(), any(), any(), any()))
                .thenReturn(new OutboxEvent());

        publisher.publishStockLowDetected(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxService).recordEvent(
                eq("Inventory"),
                eq("100"),
                eq("STOCK_LOW_DETECTED"),
                eq(ReplenishmentEventPublisher.TOPIC_STOCK_LOW_DETECTED),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).isSameAs(event);
    }

    @Test
    @DisplayName("should record stock.replenished outbox row when publishing replenishment event")
    void should_recordStockReplenishedOutboxRow_when_publishingStockReplenished() {
        StockReplenishedEvent event = new StockReplenishedEvent(
                UUID.randomUUID(),
                200L,
                "SKU-200",
                50,
                Instant.now()
        );
        when(outboxService.recordEvent(any(), any(), any(), any(), any()))
                .thenReturn(new OutboxEvent());

        publisher.publishStockReplenished(event);

        verify(outboxService).recordEvent(
                eq("Inventory"),
                eq("200"),
                eq("STOCK_REPLENISHED"),
                eq(ReplenishmentEventPublisher.TOPIC_STOCK_REPLENISHED),
                eq(event)
        );
    }

    @Test
    @DisplayName("should propagate outbox failures so the surrounding transaction rolls back")
    void should_propagateOutboxFailure_when_recordEventThrows() {
        StockLowDetectedEvent event = new StockLowDetectedEvent(
                UUID.randomUUID(),
                300L,
                "SKU-300",
                0,
                5,
                Instant.now()
        );
        when(outboxService.recordEvent(any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> publisher.publishStockLowDetected(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB down");
    }
}
