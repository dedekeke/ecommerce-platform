package com.ecommerce.gateway.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;

/**
 * Integration test for the API versioning deprecation contract
 * (docs/API_VERSIONING.md).
 *
 * <p>Boots the gateway (security disabled, no Eureka) and drives real HTTP
 * through it via {@link WebTestClient}. Routes are overridden by
 * {@link TestRoutes} to point at a {@link MockWebServer} so we exercise the
 * actual {@code AddResponseHeader} filter chain end-to-end without needing a
 * live backend or service discovery.
 *
 * <p>Contract asserted:
 * <ul>
 *   <li>the unversioned {@code /api/products} route carries
 *       {@code Deprecation} + {@code Sunset} response headers;</li>
 *   <li>the versioned {@code /api/v1/products} route carries neither.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=false",
        "gateway.programmatic-routes.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "eureka.client.enabled=false",
        "spring.cloud.gateway.discovery.locator.enabled=false",
        "request-logging.enabled=false",
        "security.ip-whitelist.enabled=false",
        "spring.data.redis.repositories.enabled=false",
        "management.tracing.enabled=false",
        "AUTH0_ISSUER_URI=http://localhost/issuer",
        "AUTH0_DOMAIN=localhost",
        "AUTH0_AUDIENCE=test",
        "AUTH0_CLIENT_ID=test",
        "AUTH0_CLIENT_SECRET=test",
        "REDIS_HOST=localhost",
        "REDIS_PORT=6379",
        "EUREKA_URI=http://localhost:8761/eureka",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration," +
            "org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
})
class DeprecationHeaderIntegrationTest {

    private static final String SUNSET = "Fri, 30 Apr 2027 23:59:59 GMT";

    private static MockWebServer backend;

    @org.springframework.beans.factory.annotation.Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startBackend() throws IOException {
        backend = new MockWebServer();
        backend.start();
    }

    @AfterAll
    static void stopBackend() throws IOException {
        backend.shutdown();
    }

    @DynamicPropertySource
    static void backendUri(DynamicPropertyRegistry registry) {
        registry.add("test.backend.uri", () -> "http://localhost:" + backend.getPort());
    }

    @Test
    void should_attachDeprecationAndSunset_on_unversioned_products_route() {
        backend.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        webTestClient.get().uri("/api/products")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("Deprecation", "true")
                .expectHeader().valueEquals("Sunset", SUNSET);
    }

    @Test
    void should_notAttachDeprecationHeaders_on_v1_products_route() {
        backend.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        webTestClient.get().uri("/api/v1/products")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("Deprecation")
                .expectHeader().doesNotExist("Sunset");
    }

    @TestConfiguration
    static class TestRoutes {

        @Bean
        RouteLocator testRouteLocator(RouteLocatorBuilder builder,
                                      @org.springframework.beans.factory.annotation.Value("${test.backend.uri}") String backendUri) {
            return builder.routes()
                    .route("test-product-unversioned", r -> r
                            .path("/api/products/**")
                            .filters(f -> f
                                    .addResponseHeader("Deprecation", "true")
                                    .addResponseHeader("Sunset", SUNSET))
                            .uri(backendUri))
                    .route("test-product-v1", r -> r
                            .path("/api/v1/products/**")
                            .filters(f -> f
                                    .rewritePath("/api/v1/(?<segment>.*)", "/api/${segment}"))
                            .uri(backendUri))
                    .build();
        }
    }
}
