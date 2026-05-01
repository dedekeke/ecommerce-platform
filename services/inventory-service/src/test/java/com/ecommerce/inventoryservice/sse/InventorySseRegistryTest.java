package com.ecommerce.inventoryservice.sse;

import com.ecommerce.inventoryservice.event.InventoryStockChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InventorySseRegistry — emitter fan-out & per-emitter filtering")
class InventorySseRegistryTest {

    private InventorySseRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new InventorySseRegistry();
    }

    @Test
    @DisplayName("register() returns a non-null SseEmitter and increments active count")
    void registerReturnsEmitter() {
        SseEmitter emitter = registry.register("session-1", null, 30_000L);

        assertThat(emitter).isNotNull();
        assertThat(registry.activeConnections()).isEqualTo(1);
    }

    @Test
    @DisplayName("broadcast() delivers events to emitters that have no filter")
    void broadcastDeliversToUnfilteredEmitters() throws IOException {
        AtomicInteger received = new AtomicInteger(0);
        SseEmitter emitter = new SseEmitter(30_000L) {
            @Override
            public void send(SseEventBuilder builder) {
                received.incrementAndGet();
            }
        };
        registry.registerExisting("session-x", emitter, null);

        registry.broadcast(stockEvent("PROD-1", 5, 10));

        assertThat(received.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("broadcast() honours per-emitter productId filter")
    void broadcastHonoursFilter() throws IOException {
        AtomicInteger received = new AtomicInteger(0);
        SseEmitter emitter = new SseEmitter(30_000L) {
            @Override
            public void send(SseEventBuilder builder) {
                received.incrementAndGet();
            }
        };
        registry.registerExisting("session-y", emitter, Set.of("PROD-WANTED"));

        registry.broadcast(stockEvent("PROD-OTHER", 1, 2));
        assertThat(received.get()).isZero();

        registry.broadcast(stockEvent("PROD-WANTED", 3, 4));
        assertThat(received.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("unregister() removes the emitter so broadcast no longer delivers")
    void unregisterRemovesEmitter() throws IOException {
        AtomicInteger received = new AtomicInteger(0);
        SseEmitter emitter = new SseEmitter(30_000L) {
            @Override
            public void send(SseEventBuilder builder) {
                received.incrementAndGet();
            }
        };
        registry.registerExisting("session-z", emitter, null);

        registry.unregister("session-z");
        registry.broadcast(stockEvent("X", 1, 2));

        assertThat(received.get()).isZero();
        assertThat(registry.activeConnections()).isZero();
    }

    @Test
    @DisplayName("broadcast() removes emitters that fail to send (IOException)")
    void brokenEmitterIsEvicted() {
        SseEmitter emitter = new SseEmitter(30_000L) {
            @Override
            public void send(SseEventBuilder builder) throws IOException {
                throw new IOException("client gone");
            }
        };
        registry.registerExisting("session-broken", emitter, null);

        registry.broadcast(stockEvent("PROD-1", 5, 10));

        assertThat(registry.activeConnections()).isZero();
    }

    @Test
    @DisplayName("heartbeat() emits a comment ping to every emitter")
    void heartbeatPingsEveryone() {
        AtomicInteger pings = new AtomicInteger(0);
        SseEmitter emitter = new SseEmitter(30_000L) {
            @Override
            public void send(SseEventBuilder builder) {
                pings.incrementAndGet();
            }
        };
        registry.registerExisting("hb-1", emitter, null);

        registry.heartbeat();

        assertThat(pings.get()).isEqualTo(1);
    }

    private InventoryStockChangedEvent stockEvent(String productId, int newQty, int prevQty) {
        return InventoryStockChangedEvent.builder()
                .productId(productId)
                .sku(productId + "-SKU")
                .availableQty(newQty)
                .previousQty(prevQty)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
