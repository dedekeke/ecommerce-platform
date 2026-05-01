package com.ecommerce.gateway.graphql;

import com.ecommerce.gateway.graphql.client.CartBackendClient;
import com.ecommerce.gateway.graphql.client.OrderBackendClient;
import com.ecommerce.gateway.graphql.client.RecommendationBackendClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke tests for the remaining backend clients to keep the graphql package
 * coverage above the 85% bar.
 */
class BackendClientsTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    private WebClient client() {
        return WebClient.builder().baseUrl(server.url("/").toString()).build();
    }

    @Test
    void should_loadCart_when_cartServiceReturnsJson() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"userId":"u","items":[],"totalAmount":0,"totalItems":0,"appliedPromotions":[]}
                        """));

        var c = new CartBackendClient(client());
        StepVerifier.create(c.getCart("u"))
                .assertNext(dto -> assertThat(dto.userId()).isEqualTo("u"))
                .verifyComplete();
    }

    @Test
    void should_returnEmpty_when_cartServiceReturns500() {
        server.enqueue(new MockResponse().setResponseCode(500));
        var c = new CartBackendClient(client());
        StepVerifier.create(c.getCart("u")).verifyComplete();
    }

    @Test
    void should_loadOrder_when_orderServiceReturnsJson() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"o1","orderNumber":"ORD-1","userId":"u","status":"PAID",
                         "totalAmount":1.0,"items":[],"createdAt":"2026-04-29T10:00:00Z"}
                        """));

        var o = new OrderBackendClient(client());
        StepVerifier.create(o.findById("o1", "u"))
                .assertNext(dto -> assertThat(dto.orderNumber()).isEqualTo("ORD-1"))
                .verifyComplete();

        var rec = server.takeRequest();
        assertThat(rec.getHeader("X-User-Id")).isEqualTo("u");
    }

    @Test
    void should_paginateOrders_when_findForUserCalled() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"content":[],"totalElements":0,"totalPages":0,"number":0}
                        """));

        var o = new OrderBackendClient(client());
        StepVerifier.create(o.findForUser("u", 0, 10))
                .assertNext(p -> assertThat(p.totalElements()).isEqualTo(0L))
                .verifyComplete();
    }

    @Test
    void should_loadRecommendations_when_recommendationServiceReturnsJson() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[{\"productId\":\"p1\",\"score\":7}]"));

        var r = new RecommendationBackendClient(client());
        StepVerifier.create(r.getForProduct("base", 5))
                .assertNext(list -> {
                    assertThat(list).hasSize(1);
                    assertThat(list.get(0).productId()).isEqualTo("p1");
                })
                .verifyComplete();
    }

    @Test
    void should_returnEmptyList_when_recommendationServiceFails() {
        server.enqueue(new MockResponse().setResponseCode(500));
        var r = new RecommendationBackendClient(client());
        StepVerifier.create(r.getForProduct("base", 5))
                .assertNext(list -> assertThat(list).isEmpty())
                .verifyComplete();
    }
}
