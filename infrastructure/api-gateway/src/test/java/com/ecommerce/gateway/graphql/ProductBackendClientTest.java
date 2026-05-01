package com.ecommerce.gateway.graphql;

import com.ecommerce.gateway.graphql.client.ProductBackendClient;
import com.ecommerce.gateway.graphql.dto.PageDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives {@link ProductBackendClient} against a real HTTP server (MockWebServer)
 * to verify URI shape, JSON deserialization to the BFF DTOs, and graceful
 * 5xx handling.
 *
 * <p>We intentionally bypass the {@code lb://} URI here — the load-balancer
 * resolution is a Spring Cloud concern, not the BFF's responsibility.
 */
class ProductBackendClientTest {

    private MockWebServer server;
    private ProductBackendClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        WebClient wc = WebClient.builder().baseUrl(server.url("/").toString()).build();
        client = new ProductBackendClient(wc);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void should_deserializeProduct_when_findByIdSucceeds() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"id":"42","name":"W","price":9.99,"currency":"USD",
                         "images":["i.png"],"stockQuantity":3,"inStock":true}
                        """));

        StepVerifier.create(client.findById("42"))
                .assertNext(p -> {
                    assertThat(p.id()).isEqualTo("42");
                    assertThat(p.name()).isEqualTo("W");
                    assertThat(p.resolvedInStock()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void should_returnEmpty_when_findByIdReturns500() {
        server.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(client.findById("42"))
                .verifyComplete(); // mapped to empty Mono
    }

    @Test
    void should_returnEmptyMap_when_findByIdsCalledWithEmptyList() {
        StepVerifier.create(client.findByIds(List.of()))
                .assertNext(map -> assertThat(map).isEmpty())
                .verifyComplete();
    }

    @Test
    void should_buildSearchUri_when_searchCalled() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"content":[{"id":"1","name":"A","price":1.0,"currency":"USD"}],
                         "totalElements":1,"totalPages":1,"number":0}
                        """));

        StepVerifier.create(client.search("term", "books", 0, 20))
                .assertNext((PageDto<ProductDto> page) -> {
                    assertThat(page.content()).hasSize(1);
                    assertThat(page.totalElements()).isEqualTo(1L);
                })
                .verifyComplete();

        var recorded = server.takeRequest();
        assertThat(recorded.getPath()).contains("/api/products");
        assertThat(recorded.getPath()).contains("search=term");
        assertThat(recorded.getPath()).contains("categoryId=books");
    }

    @Test
    void should_collectMap_when_findByIdsHasMultipleKeys() {
        server.enqueue(jsonProduct("p1"));
        server.enqueue(jsonProduct("p2"));

        StepVerifier.create(client.findByIds(List.of("p1", "p2")))
                .assertNext((Map<String, ProductDto> map) -> {
                    assertThat(map).containsKeys("p1", "p2");
                })
                .verifyComplete();
    }

    private MockResponse jsonProduct(String id) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"" + id + "\",\"name\":\"n\",\"price\":1.0,\"currency\":\"USD\"}");
    }
}
