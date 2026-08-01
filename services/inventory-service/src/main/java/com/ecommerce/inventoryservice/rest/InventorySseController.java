package com.ecommerce.inventoryservice.rest;

import com.ecommerce.inventoryservice.event.InventoryStockChangedEvent;
import com.ecommerce.inventoryservice.sse.InventorySseRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Server-Sent Events stream of inventory stock changes. Clients open the
 * endpoint with the browser-native {@code EventSource} API; the connection is
 * one-way (server -> client) and auto-reconnects on transport failure.
 *
 * <p>Events emitted under the {@code stock-update} event name carry an
 * {@link InventoryStockChangedEvent} JSON payload. Optional
 * {@code productIds=PROD-1,PROD-2} query parameter filters delivery to a
 * caller-defined slice.
 *
 * <p>Connection accounting and fan-out is delegated to
 * {@link InventorySseRegistry}; this controller owns nothing but the HTTP
 * adapter and the lifecycle wiring.
 */
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Inventory SSE", description = "Real-time inventory stock-level stream")
public class InventorySseController {

    private final InventorySseRegistry registry;

    @Value("${inventory.sse.timeout-ms:30000}")
    private long sseTimeoutMs;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
            summary = "Stream inventory stock-level changes",
            description = "Returns a text/event-stream connection that receives a 'stock-update' event whenever any (or selected) product's available quantity changes."
    )
    public SseEmitter stream(
            @RequestParam(value = "productIds", required = false) String productIds,
            HttpServletResponse response) {

        // Disable response buffering so events are flushed instantly even when
        // a reverse proxy sits in front of the service.
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        Set<String> filter = parseFilter(productIds);
        String sessionId = UUID.randomUUID().toString();

        log.debug("SSE open session={} filter={}", sessionId, filter);
        return registry.register(sessionId, filter, sseTimeoutMs);
    }

    /**
     * Bridge: when the inventory service publishes a stock-changed event,
     * fan it out to all open emitters.
     */
    @EventListener
    public void onStockChanged(InventoryStockChangedEvent event) {
        registry.broadcast(event);
    }

    /**
     * Heartbeat ping: keep proxies / load balancers from closing idle
     * connections. Interval is configured by
     * {@code inventory.sse.heartbeat-interval-ms} (default 15 s).
     */
    @Scheduled(fixedRateString = "${inventory.sse.heartbeat-interval-ms:15000}")
    public void heartbeat() {
        registry.heartbeat();
    }

    private static Set<String> parseFilter(String productIds) {
        if (productIds == null || productIds.isBlank()) {
            return null;
        }
        return Arrays.stream(productIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
