package com.ecommerce.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.config.GatewayProperties;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewritePathGatewayFilterFactory;
import org.springframework.cloud.gateway.filter.factory.TokenRelayGatewayFilterFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.stream.Stream;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GatewayRoutesConfig}.
 *
 * <p>Exercises the route locator directly without spinning up the full
 * gateway context. We assert two contracts:
 * <ol>
 *   <li>For every backend service we expose, both an unversioned route
 *       (e.g. {@code /api/products/**}) and a versioned route
 *       (e.g. {@code /api/v1/products/**}) exist.</li>
 *   <li>The versioned routes are present in the route table — the actual
 *       prefix-strip behaviour is exercised by the
 *       {@link com.ecommerce.gateway.config.ApiVersioningRoutingTest}
 *       reactive integration test.</li>
 * </ol>
 */
class GatewayRoutesConfigTest {

    private static final List<String> EXPECTED_PROG_ROUTE_IDS = List.of(
            "user-service-prog", "user-service-v1-prog",
            "wishlist-service-prog", "wishlist-service-v1-prog",
            "product-service-prog", "product-service-v1-prog",
            "cart-service-prog", "cart-service-v1-prog",
            "order-service-prog", "order-service-v1-prog",
            "payment-service-prog", "payment-service-v1-prog",
            "inventory-service-prog", "inventory-service-v1-prog",
            "notification-service-prog", "notification-service-v1-prog",
            "search-service-prog", "search-service-v1-prog",
            "media-service-prog", "media-service-v1-prog",
            "promotion-service-prog", "promotion-service-v1-prog",
            "recommendation-service-prog", "recommendation-service-v1-prog",
            "review-service-prog", "review-service-v1-prog"
    );

    @Test
    void should_register_unversioned_and_v1_route_for_every_service() {
        List<Route> routes = buildRoutes();
        List<String> ids = routes.stream().map(r -> r.getId()).toList();

        assertThat(ids).containsAll(EXPECTED_PROG_ROUTE_IDS);
    }

    @Test
    void should_include_review_service_routes() {
        List<Route> routes = buildRoutes();
        assertThat(routes.stream().anyMatch(r -> "review-service-prog".equals(r.getId()))).isTrue();
        assertThat(routes.stream().anyMatch(r -> "review-service-v1-prog".equals(r.getId()))).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "user-service-v1-prog,         lb://user-service",
            "wishlist-service-v1-prog,     lb://user-service",
            "product-service-v1-prog,      lb://product-service",
            "cart-service-v1-prog,         lb://cart-service",
            "order-service-v1-prog,        lb://order-service",
            "payment-service-v1-prog,      lb://payment-service",
            "inventory-service-v1-prog,    lb://inventory-service",
            "notification-service-v1-prog, lb://notification-service",
            "search-service-v1-prog,       lb://search-service",
            "media-service-v1-prog,        lb://media-service",
            "promotion-service-v1-prog,    lb://promotion-service",
            "recommendation-service-v1-prog, lb://recommendation-service",
            "review-service-v1-prog,       lb://review-service"
    })
    void should_route_v1_routes_to_correct_load_balanced_service(String routeId, String expectedUri) {
        List<Route> routes = buildRoutes();
        Route route = routes.stream()
                .filter(r -> routeId.equals(r.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(route.getUri().toString()).isEqualTo(expectedUri);
    }

    @Test
    void should_have_at_least_24_v1_or_unversioned_routes_total() {
        // 12 backend services × 2 (unversioned + v1) = 24, plus wishlist (×2)
        // and 4 admin routes.
        assertThat(buildRoutes()).hasSizeGreaterThanOrEqualTo(24);
    }

    @ParameterizedTest
    @CsvSource({
            // every unversioned route is sunset-stamped
            "user-service-prog",
            "wishlist-service-prog",
            "product-service-prog",
            "cart-service-prog",
            "order-service-prog",
            "payment-service-prog",
            "inventory-service-prog",
            "notification-service-prog",
            "search-service-prog",
            "media-service-prog",
            "promotion-service-prog",
            "recommendation-service-prog",
            "review-service-prog"
    })
    void should_attachDeprecationFilters_only_on_unversioned_routes(String routeId) {
        // We assert by string-matching the registered filter list — Spring Cloud
        // Gateway doesn't expose strongly-typed accessors for the AddResponseHeader
        // factory's args. Filter#toString() is stable enough for this.
        Route route = buildRoutes().stream()
                .filter(r -> routeId.equals(r.getId()))
                .findFirst()
                .orElseThrow();
        String filters = route.getFilters().toString();
        assertThat(filters).contains("Deprecation");
        assertThat(filters).contains("Sunset");
    }

    @ParameterizedTest
    @CsvSource({
            "user-service-v1-prog",
            "wishlist-service-v1-prog",
            "product-service-v1-prog",
            "cart-service-v1-prog",
            "order-service-v1-prog",
            "payment-service-v1-prog",
            "inventory-service-v1-prog",
            "notification-service-v1-prog",
            "search-service-v1-prog",
            "media-service-v1-prog",
            "promotion-service-v1-prog",
            "recommendation-service-v1-prog",
            "review-service-v1-prog"
    })
    void should_NOT_attachDeprecationFilters_on_v1_routes(String routeId) {
        Route route = buildRoutes().stream()
                .filter(r -> routeId.equals(r.getId()))
                .findFirst()
                .orElseThrow();
        String filters = route.getFilters().toString();
        assertThat(filters).doesNotContain("Deprecation");
        assertThat(filters).doesNotContain("Sunset");
    }

    private static List<Route> buildRoutes() {
        GatewayRoutesConfig config = new GatewayRoutesConfig();
        ReflectionTestUtils.setField(config, "sunset", "Fri, 30 Apr 2027 23:59:59 GMT");

        // RouteLocatorBuilder requires a ConfigurableApplicationContext to
        // resolve filter/predicate factories. We provide a minimal context with
        // the factories the routes actually use, including a TokenRelay factory
        // wired against an empty ObjectProvider — we don't actually authenticate
        // anything in this test, we only assert the route table topology.
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean(AddResponseHeaderGatewayFilterFactory.class);
        ctx.registerBean(AddRequestHeaderGatewayFilterFactory.class);
        ctx.registerBean(RewritePathGatewayFilterFactory.class);
        ctx.registerBean(PathRoutePredicateFactory.class);
        ctx.registerBean(MethodRoutePredicateFactory.class);
        ctx.registerBean(GatewayProperties.class);
        ctx.registerBean(TokenRelayGatewayFilterFactory.class,
                () -> new TokenRelayGatewayFilterFactory(emptyClientManagerProvider()));
        ctx.refresh();

        RouteLocatorBuilder builder = new RouteLocatorBuilder(ctx);
        try {
            RouteLocator locator = config.customRouteLocator(builder);
            return Flux.from(locator.getRoutes()).collectList().block();
        } finally {
            ctx.close();
        }
    }

    /**
     * Empty {@link ObjectProvider} stand-in for the
     * {@link ReactiveOAuth2AuthorizedClientManager} dependency of the
     * TokenRelay filter. The actual TokenRelay filter is never invoked in
     * these tests — we only need it to satisfy the bean dependency so the
     * route registration succeeds.
     */
    private static ObjectProvider<ReactiveOAuth2AuthorizedClientManager> emptyClientManagerProvider() {
        return new ObjectProvider<>() {
            @Override public ReactiveOAuth2AuthorizedClientManager getObject(Object... args) { return null; }
            @Override public ReactiveOAuth2AuthorizedClientManager getObject() { return null; }
            @Override public ReactiveOAuth2AuthorizedClientManager getIfAvailable() { return null; }
            @Override public ReactiveOAuth2AuthorizedClientManager getIfUnique() { return null; }
            @Override public Stream<ReactiveOAuth2AuthorizedClientManager> stream() { return Stream.empty(); }
        };
    }
}
