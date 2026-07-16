package com.ecommerce.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.handler.DefaultWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authorization-matrix tests for the API-Gateway {@link SecurityWebFilterChain},
 * guarding against the versioned/unversioned auth policy drift (Lore 2b8c4227):
 * {@code GET /api/products} was {@code permitAll} while {@code GET /api/v1/products}
 * fell through to {@code anyExchange().authenticated()} — same resource, different
 * policy by version.
 *
 * <p>The chain is booted with security ENABLED and driven directly with a
 * {@link MockServerWebExchange}, bypassing the servlet/HTTP layer and the
 * unrelated {@code SecurityHeadersFilter}. A terminal {@link WebFilterChain}
 * records whether the request reached the (proxied) backend:
 * <ul>
 *   <li><b>permitted</b> — the request passes every security filter, the
 *       terminal handler runs, and no {@code 401} is written; </li>
 *   <li><b>denied</b> — {@code ExceptionTranslationWebFilter} commits
 *       {@code 401 UNAUTHORIZED} via the Bearer entry point and the terminal
 *       handler is never reached.</li>
 * </ul>
 *
 * <p>The chain evaluates the ORIGINAL request path; the v1 {@code RewritePath}
 * filter runs later during routing, which is precisely why {@code /api/v1/*}
 * was not covered by the unversioned catalog rules.
 *
 * <p>Only the UNAUTHENTICATED matrix is exercised (no bearer token): catalog
 * reads must pass, everything else must be denied. That fully demonstrates the
 * catalog-read drift fix and guards the locked routes. The write-path
 * tightening (v1 product writes: {@code authenticated} -> {@code SCOPE_admin})
 * is not observable without a valid non-admin token — both policies yield 401
 * when unauthenticated — so it is called out in the PR for tech-lead review.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
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
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/issuer",
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
class SecurityPolicyDriftTest {

    @Autowired
    private SecurityWebFilterChain securityWebFilterChain;

    // --- Catalog reads: MUST be public on BOTH versions after the fix ---

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/products",
            "/api/products/42",
            "/api/v1/products",           // reported drift: authenticated before the fix
            "/api/v1/products/42",
            "/api/categories",            // unversioned aligned to the v1 public rule
            "/api/categories/electronics",
            "/api/v1/categories",
            "/api/v1/categories/electronics",
            "/api/search/laptops",
            "/api/v1/search/laptops",     // drift
            "/api/promotions/public/summer",
            "/api/v1/promotions/public/summer" // drift
    })
    void should_permitUnauthenticatedGet_when_catalogReadPath(String path) {
        AuthzResult result = runGet(path);
        assertThat(result.reachedBackend())
                .as("GET %s should be public (reach backend)", path)
                .isTrue();
    }

    // --- Catalog writes: never public, on either version ---

    @ParameterizedTest
    @ValueSource(strings = {"/api/products", "/api/v1/products"})
    void should_denyUnauthenticatedPost_when_productWritePath(String path) {
        AuthzResult result = runPost(path);
        assertThat(result.denied401())
                .as("POST %s must be denied for anonymous callers", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products/42", "/api/v1/products/42"})
    void should_denyUnauthenticatedPut_when_productWritePath(String path) {
        assertThat(run(MockServerHttpRequest.put(path)).denied401())
                .as("PUT %s must be denied for anonymous callers", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products/42", "/api/v1/products/42"})
    void should_denyUnauthenticatedDelete_when_productWritePath(String path) {
        assertThat(run(MockServerHttpRequest.delete(path)).denied401())
                .as("DELETE %s must be denied for anonymous callers", path)
                .isTrue();
    }

    // --- Non-catalog routes: stay authenticated on BOTH versions ---

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/cart",
            "/api/v1/cart",
            "/api/orders",
            "/api/v1/orders",
            "/api/payments",
            "/api/v1/payments",
            "/api/users/me",
            "/api/v1/users/me",
            "/api/admin/products",
            "/api/reviews",               // no public signal -> stays protected on both
            "/api/v1/reviews"
    })
    void should_denyUnauthenticatedGet_when_protectedPath(String path) {
        AuthzResult result = runGet(path);
        assertThat(result.denied401())
                .as("GET %s must remain authenticated", path)
                .isTrue();
    }

    @Test
    void should_denyUnauthenticatedGet_when_versionedProductWriteAnalogueStillProtected() {
        // Sanity: a v1 non-catalog path (recommendations has no public rule) is denied.
        assertThat(runGet("/api/v1/recommendations").denied401()).isTrue();
    }

    // --- Stripe webhook: POST must be public on BOTH versions (Stripe carries no
    //     JWT; payment-service authenticates via the Stripe-Signature HMAC), while
    //     every other /api/payments/** call and other methods stay authenticated. ---

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments/webhook", "/api/v1/payments/webhook"})
    void should_permitUnauthenticatedPost_when_stripeWebhookPath(String path) {
        AuthzResult result = runPost(path);
        assertThat(result.reachedBackend())
                .as("POST %s must be public so Stripe (no JWT) reaches the service", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/payments/webhook", "/api/v1/payments/webhook"})
    void should_denyUnauthenticatedGet_when_stripeWebhookPath(String path) {
        // The exemption is POST-only: a GET to the same path stays authenticated.
        assertThat(runGet(path).denied401())
                .as("GET %s must not inherit the POST-only webhook exemption", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/payments/intents",
            "/api/v1/payments/intents",
            "/api/payments/webhook/extra",   // exact-path exemption must not match sub-paths
            "/api/payments"
    })
    void should_denyUnauthenticatedPost_when_nonWebhookPaymentPath(String path) {
        assertThat(runPost(path).denied401())
                .as("POST %s must stay authenticated (only the exact webhook path is public)", path)
                .isTrue();
    }

    // --- Guest checkout: POST /api/orders/guest must be public on BOTH versions
    //     (an anonymous shopper carries no JWT; order-service derives identity
    //     from the email), while the authenticated create and every other order
    //     call stay authenticated. ---

    @ParameterizedTest
    @ValueSource(strings = {"/api/orders/guest", "/api/v1/orders/guest"})
    void should_permitUnauthenticatedPost_when_guestCheckoutPath(String path) {
        AuthzResult result = runPost(path);
        assertThat(result.reachedBackend())
                .as("POST %s must be public so an anonymous guest reaches the service", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/orders/guest", "/api/v1/orders/guest"})
    void should_denyUnauthenticatedGet_when_guestCheckoutPath(String path) {
        // The exemption is POST-only: a GET to the same path stays authenticated.
        assertThat(runGet(path).denied401())
                .as("GET %s must not inherit the POST-only guest exemption", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/orders", "/api/v1/orders"})
    void should_denyUnauthenticatedPost_when_authenticatedOrderCreatePath(String path) {
        // The hardened authenticated create must NOT be weakened by the guest
        // exemption — it still requires a JWT.
        assertThat(runPost(path).denied401())
                .as("POST %s (authenticated create) must stay authenticated", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/orders/guest/extra",       // exact-path exemption must not match sub-paths
            "/api/v1/orders/guest/extra",
            "/api/orders/12345"              // a real order id is not the guest path
    })
    void should_denyUnauthenticatedPost_when_nonGuestOrderPath(String path) {
        assertThat(runPost(path).denied401())
                .as("POST %s must stay authenticated (only the exact guest path is public)", path)
                .isTrue();
    }

    // --- Authenticated matrix: catalog writes require SCOPE_admin on both versions.
    //     A non-admin JWT must be FORBIDDEN (403); an admin JWT must pass through.
    //     PATCH is the red-first case: without a PATCH matcher it fell through to
    //     authenticated(), so a non-admin could PATCH stock / move categories. ---

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/products/42",
            "/api/v1/products/42",
            "/api/products/42/stock",       // PATCH stock — the reported HIGH gap
            "/api/v1/products/42/stock",
            "/api/categories/7",
            "/api/v1/categories/7",
            "/api/v1/categories/7/move"     // PATCH move — the reported HIGH gap
    })
    void should_denyNonAdmin_when_catalogPatch(String path) {
        AuthzResult result = run(MockServerHttpRequest.patch(path), nonAdminJwt());
        assertThat(result.denied403())
                .as("PATCH %s must require SCOPE_admin, not just authentication", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products/42/stock", "/api/v1/categories/7/move"})
    void should_permitAdmin_when_catalogPatch(String path) {
        AuthzResult result = run(MockServerHttpRequest.patch(path), adminJwt());
        assertThat(result.reachedBackend())
                .as("PATCH %s must be allowed for SCOPE_admin", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products/42", "/api/v1/products/42", "/api/categories/7", "/api/v1/categories/7"})
    void should_denyNonAdmin_when_catalogPost(String path) {
        assertThat(run(MockServerHttpRequest.post(path), nonAdminJwt()).denied403())
                .as("POST %s must require SCOPE_admin", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products/42", "/api/v1/products/42", "/api/categories/7", "/api/v1/categories/7"})
    void should_denyNonAdmin_when_catalogPut(String path) {
        assertThat(run(MockServerHttpRequest.put(path), nonAdminJwt()).denied403())
                .as("PUT %s must require SCOPE_admin", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products/42", "/api/v1/products/42", "/api/categories/7", "/api/v1/categories/7"})
    void should_denyNonAdmin_when_catalogDelete(String path) {
        assertThat(run(MockServerHttpRequest.delete(path), nonAdminJwt()).denied403())
                .as("DELETE %s must require SCOPE_admin", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products/42", "/api/v1/products/42", "/api/categories/7", "/api/v1/categories/7"})
    void should_permitAdmin_when_catalogPost(String path) {
        assertThat(run(MockServerHttpRequest.post(path), adminJwt()).reachedBackend())
                .as("POST %s must be allowed for SCOPE_admin", path)
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/products/42", "/api/v1/products/42", "/api/categories/7", "/api/v1/categories/7"})
    void should_permitAdmin_when_catalogDelete(String path) {
        assertThat(run(MockServerHttpRequest.delete(path), adminJwt()).reachedBackend())
                .as("DELETE %s must be allowed for SCOPE_admin", path)
                .isTrue();
    }

    @Test
    void should_permitNonAdmin_when_catalogGet() {
        // Reads stay public even for an authenticated non-admin (no privilege needed).
        assertThat(run(MockServerHttpRequest.get("/api/v1/products/42"), nonAdminJwt()).reachedBackend())
                .isTrue();
    }

    // --- helpers ---

    private AuthzResult runGet(String path) {
        return run(MockServerHttpRequest.get(path), null);
    }

    private AuthzResult runPost(String path) {
        return run(MockServerHttpRequest.post(path), null);
    }

    private AuthzResult run(MockServerHttpRequest.BaseBuilder<?> builder) {
        return run(builder, null);
    }

    private AuthzResult run(MockServerHttpRequest.BaseBuilder<?> builder, Authentication authentication) {
        MockServerWebExchange exchange = MockServerWebExchange.from(builder.build());
        AtomicBoolean reached = new AtomicBoolean(false);

        List<WebFilter> filters = securityWebFilterChain.getWebFilters().collectList().block();
        WebFilterChain chain = new DefaultWebFilterChain(ex -> {
            reached.set(true);
            ex.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        }, filters);

        Mono<Void> execution = chain.filter(exchange);
        if (authentication != null) {
            execution = execution.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
        }
        execution.block();

        ServerHttpResponse response = exchange.getResponse();
        return new AuthzResult(reached.get(), response.getStatusCode());
    }

    private static Authentication adminJwt() {
        return jwt(new SimpleGrantedAuthority("SCOPE_admin"));
    }

    private static Authentication nonAdminJwt() {
        return jwt(new SimpleGrantedAuthority("SCOPE_profile"));
    }

    private static Authentication jwt(SimpleGrantedAuthority... authorities) {
        Jwt token = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("user-123")
                .claim("scope", "read")
                .build();
        return new JwtAuthenticationToken(token, List.of(authorities));
    }

    private record AuthzResult(boolean reachedBackend, HttpStatusCode statusCode) {
        boolean denied401() {
            return statusDenied(HttpStatus.UNAUTHORIZED);
        }

        boolean denied403() {
            return statusDenied(HttpStatus.FORBIDDEN);
        }

        private boolean statusDenied(HttpStatus expected) {
            return !reachedBackend
                    && statusCode != null
                    && statusCode.value() == expected.value();
        }
    }
}
