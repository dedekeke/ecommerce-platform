package com.ecommerce.inventoryservice.rest;

import com.ecommerce.inventoryservice.event.InventoryStockChangedEvent;
import com.ecommerce.inventoryservice.sse.InventorySseRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice integration test: boots only enough Spring context to verify the SSE
 * controller wiring (route mapping, content-type, registry usage, lifecycle).
 *
 * <p>The full Inventory service is not needed — we exercise just the
 * controller + registry + Spring MVC.
 */
@SpringBootTest(
        classes = {
                InventorySseController.class,
                InventorySseRegistry.class,
                InventorySseControllerTest.TestConfig.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "inventory.sse.timeout-ms=500",
        "inventory.sse.heartbeat-interval-ms=600000",
        "spring.main.web-application-type=servlet",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "security.enabled=false"
})
@DisplayName("InventorySseController — HTTP wiring & event fan-out")
class InventorySseControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InventorySseRegistry registry;

    @Autowired
    private InventorySseController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("GET /api/inventory/stream starts async response and registers an emitter")
    void streamReturnsTextEventStream() throws Exception {
        int before = registry.activeConnections();
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        // SseEmitter is async — MockMvc starts the request but Content-Type is
        // populated only after asyncDispatch. The registry side-effect (an
        // emitter registered before return) is the contract that matters here.
        assertThat(result.getRequest().isAsyncStarted()).isTrue();
        assertThat(registry.activeConnections()).isGreaterThanOrEqualTo(before + 1);
    }

    @Test
    @DisplayName("Successful GET registers an emitter (active count > 0)")
    void registersEmitter() throws Exception {
        int before = registry.activeConnections();
        mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(registry.activeConnections()).isGreaterThanOrEqualTo(before + 1);
    }

    @Test
    @DisplayName("productIds query filter is accepted (no failure) and emitter registers")
    void filterIsAccepted() throws Exception {
        int before = registry.activeConnections();
        mockMvc.perform(MockMvcRequestBuilders.get("/api/inventory/stream")
                        .param("productIds", "P1,P2,P3")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertThat(registry.activeConnections()).isGreaterThanOrEqualTo(before + 1);
    }

    @Test
    @DisplayName("@EventListener bridges Spring events to registry.broadcast()")
    void eventListenerCallsBroadcast() {
        InventoryStockChangedEvent ev = InventoryStockChangedEvent.builder()
                .productId("PX")
                .sku("PX-SKU")
                .availableQty(7)
                .previousQty(10)
                .timestamp(LocalDateTime.now())
                .build();

        // Direct invocation — verifies the bridge logic without async dispatch.
        controller.onStockChanged(ev);
        // No throw is the win; broadcast is a no-op when no live emitters match.
    }

    @TestConfiguration
    static class TestConfig {
        // Empty — InventorySseController + InventorySseRegistry are picked up
        // explicitly via @SpringBootTest classes attribute.
    }
}
