package com.ecommerce.inventoryservice.sse;

import com.ecommerce.inventoryservice.event.InventoryStockChangedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds every open {@link SseEmitter} keyed by sessionId and fans out
 * inventory stock-change events. Connection life-cycle (timeout, completion,
 * error) is the only path that removes entries — the controller wires those
 * callbacks at register-time.
 *
 * <p>Per-emitter optional filter set: when present, only events whose
 * productId is in the set are delivered. Useful for catalog pages that only
 * care about a small slice.
 */
@Component
@Slf4j
public class InventorySseRegistry {

    private static final String STOCK_EVENT_NAME = "stock-update";

    private final Map<String, EmitterHandle> emitters = new ConcurrentHashMap<>();

    /**
     * Build an emitter for the given session, register it with the supplied
     * filter, and wire completion/timeout/error callbacks to {@link #unregister(String)}.
     *
     * @param sessionId         caller-supplied unique key (UUID or auth subject)
     * @param productIdFilter   {@code null} or empty = receive everything
     * @param timeoutMs         emitter timeout; 0 = default Spring timeout
     */
    public SseEmitter register(String sessionId, Set<String> productIdFilter, long timeoutMs) {
        SseEmitter emitter = timeoutMs > 0 ? new SseEmitter(timeoutMs) : new SseEmitter();
        registerExisting(sessionId, emitter, productIdFilter);

        emitter.onCompletion(() -> unregister(sessionId));
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for session {}", sessionId);
            emitter.complete();
            unregister(sessionId);
        });
        emitter.onError(ex -> {
            log.debug("SSE error for session {}: {}", sessionId, ex.toString());
            unregister(sessionId);
        });

        log.debug("SSE register session={} filter={} active={}", sessionId, productIdFilter, emitters.size());
        return emitter;
    }

    /**
     * Registers an externally constructed emitter without wiring lifecycle
     * callbacks. Used by tests; production code should call {@link #register(String, Set, long)}.
     */
    public void registerExisting(String sessionId, SseEmitter emitter, Set<String> productIdFilter) {
        emitters.put(sessionId, new EmitterHandle(emitter, productIdFilter));
    }

    public void unregister(String sessionId) {
        EmitterHandle removed = emitters.remove(sessionId);
        if (removed != null) {
            log.debug("SSE unregister session={} active={}", sessionId, emitters.size());
        }
    }

    public int activeConnections() {
        return emitters.size();
    }

    /**
     * Push the event to all emitters whose filter (if any) matches. Emitters
     * that fail to send are evicted — the client {@code EventSource} will
     * auto-reconnect.
     */
    public void broadcast(InventoryStockChangedEvent event) {
        if (emitters.isEmpty()) {
            return;
        }
        emitters.forEach((sessionId, handle) -> {
            if (!handle.matches(event.getProductId())) {
                return;
            }
            try {
                handle.emitter.send(SseEmitter.event()
                        .name(STOCK_EVENT_NAME)
                        .id(sessionId + ":" + event.getTimestamp())
                        .data(event));
            } catch (IOException | IllegalStateException ex) {
                log.debug("Evicting emitter session={} cause={}", sessionId, ex.toString());
                emitters.remove(sessionId);
            }
        });
    }

    /**
     * Send a comment-only ping to every emitter; keeps proxies / load balancers
     * from closing idle SSE connections.
     */
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        emitters.forEach((sessionId, handle) -> {
            try {
                handle.emitter.send(SseEmitter.event().comment("hb"));
            } catch (IOException | IllegalStateException ex) {
                log.debug("Heartbeat evict session={} cause={}", sessionId, ex.toString());
                emitters.remove(sessionId);
            }
        });
    }

    private static final class EmitterHandle {
        final SseEmitter emitter;
        final Set<String> productIdFilter;

        EmitterHandle(SseEmitter emitter, Set<String> productIdFilter) {
            this.emitter = emitter;
            this.productIdFilter = (productIdFilter == null || productIdFilter.isEmpty())
                    ? null
                    : Set.copyOf(productIdFilter);
        }

        boolean matches(String productId) {
            return productIdFilter == null || productIdFilter.contains(productId);
        }
    }
}
