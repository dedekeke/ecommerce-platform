package com.ecommerce.gateway.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Functional proof that the gateway strips the inter-service credential
 * {@code X-Internal-Service-Token} from inbound requests before forwarding.
 *
 * <p>That header authorizes service-only endpoints downstream — notably
 * {@code POST /api/promotions/apply}, which increments promotion usage counters.
 * It is minted by services calling each other directly over the internal
 * network, never by a client, so a value arriving from the public edge must be
 * dropped: otherwise a browser that learned the secret (leaked bundle, log,
 * proxy) could replay it through the gateway and redeem promotions at will.
 *
 * <p>Asserting the {@code RemoveRequestHeader} line in {@code application.yml}
 * is not proof — a filter can be present yet mis-ordered, mis-cased or shadowed.
 * This drives real HTTP through the booted gateway into a {@link MockWebServer}
 * and inspects the request the BACKEND actually received.
 *
 * <p>Both the canonical casing and a lower-case variant are exercised: HTTP
 * header names are case-insensitive, so a filter that matched case-sensitively
 * would leak the credential to any attacker who simply lower-cased the header.
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
@DisplayName("Gateway Internal-Token Header Strip Tests")
class InternalTokenHeaderStripIntegrationTest {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Service-Token";
    private static final String ATTACKER_VALUE = "attacker-value";

    private static MockWebServer backend;

    @Autowired
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

    /**
     * Declares the test route AS PROPERTIES and re-applies the PRODUCTION
     * {@code spring.cloud.gateway.default-filters} list.
     *
     * <p>Two traps this avoids:
     * <ul>
     *   <li>The {@code test} profile deliberately wipes {@code default-filters}
     *       (it exists to boot the gateway without Redis/Eureka), so a
     *       behavioural test run under it would exercise NO filters and pass
     *       vacuously. Rather than hand-copying the filter list — which would
     *       only prove that Spring Cloud Gateway's {@code RemoveRequestHeader}
     *       works — the shipped {@code application.yml} is loaded and its
     *       default-filter entries are replayed, so deleting that line from the
     *       real config fails THIS test too, not just the config-fidelity one.</li>
     *   <li>The route must be declared as a RouteDefinition (properties), not via
     *       {@code RouteLocatorBuilder}: programmatic routes bypass
     *       {@code RouteDefinitionRouteLocator} and therefore never inherit
     *       default-filters at all (the same reason the programmatic locator is
     *       disabled in production — see application.yml "Route authority").</li>
     * </ul>
     */
    @DynamicPropertySource
    @SuppressWarnings("unchecked")
    static void gatewayProperties(DynamicPropertyRegistry registry) throws IOException {
        registry.add("spring.cloud.gateway.routes[0].id", () -> "test-strip-internal-token");
        registry.add("spring.cloud.gateway.routes[0].uri", () -> "http://localhost:" + backend.getPort());
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/products/**");

        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        Map<String, Object> production = (Map<String, Object>) sources.get(0).getSource();

        production.entrySet().stream()
                .filter(e -> e.getKey().startsWith("spring.cloud.gateway.default-filters"))
                .forEach(e -> registry.add(e.getKey(), () -> String.valueOf(e.getValue())));
    }

    @Test
    @DisplayName("should_stripInternalServiceTokenHeader_when_clientSuppliesCanonicalCasing")
    void should_stripInternalServiceTokenHeader_when_clientSuppliesCanonicalCasing() throws Exception {
        RecordedRequest forwarded = forwardWithHeader(INTERNAL_TOKEN_HEADER);

        assertThat(forwarded.getHeader(INTERNAL_TOKEN_HEADER))
                .as("the gateway must not forward a client-supplied service credential")
                .isNull();
    }

    /**
     * Header names are case-insensitive on the wire; a case-sensitive strip
     * would be trivially bypassed by lower-casing the header.
     */
    @Test
    @DisplayName("should_stripInternalServiceTokenHeader_when_clientSuppliesLowerCaseVariant")
    void should_stripInternalServiceTokenHeader_when_clientSuppliesLowerCaseVariant() throws Exception {
        RecordedRequest forwarded = forwardWithHeader("x-internal-service-token");

        assertThat(forwarded.getHeader(INTERNAL_TOKEN_HEADER))
                .as("a lower-cased header must be stripped too — header names are case-insensitive")
                .isNull();
    }

    /**
     * Guards against the assertion above passing vacuously: the attacker value
     * must appear nowhere in the forwarded request's headers, under any name.
     */
    @Test
    @DisplayName("should_leakNoInternalTokenValue_when_clientSuppliesBothCasings")
    void should_leakNoInternalTokenValue_when_clientSuppliesBothCasings() throws Exception {
        backend.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        webTestClient.get().uri("/api/products")
                .header(INTERNAL_TOKEN_HEADER, ATTACKER_VALUE)
                .header("x-internal-service-token", ATTACKER_VALUE)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = backend.takeRequest(5, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeaders().toString())
                .as("no header of any name may carry the credential downstream")
                .doesNotContain(ATTACKER_VALUE);
    }

    /**
     * Sanity check on the harness: an unrelated custom header DOES reach the
     * backend, proving the assertions above detect a real strip rather than a
     * broken route or a request that never arrived.
     */
    @Test
    @DisplayName("should_forwardUnrelatedHeaders_when_proxyingRequest")
    void should_forwardUnrelatedHeaders_when_proxyingRequest() throws Exception {
        backend.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        webTestClient.get().uri("/api/products")
                .header("X-Unrelated-Header", "kept")
                .header(INTERNAL_TOKEN_HEADER, ATTACKER_VALUE)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = backend.takeRequest(5, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeader("X-Unrelated-Header")).isEqualTo("kept");
    }

    private RecordedRequest forwardWithHeader(String headerName) throws InterruptedException {
        backend.enqueue(new MockResponse().setResponseCode(200).setBody("[]"));

        webTestClient.get().uri("/api/products")
                .header(headerName, ATTACKER_VALUE)
                .exchange()
                .expectStatus().isOk();

        RecordedRequest forwarded = backend.takeRequest(5, TimeUnit.SECONDS);
        assertThat(forwarded).as("the gateway should have proxied the request to the backend").isNotNull();
        return forwarded;
    }

}
