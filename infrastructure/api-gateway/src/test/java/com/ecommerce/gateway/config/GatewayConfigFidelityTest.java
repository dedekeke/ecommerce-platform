package com.ecommerce.gateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Config-fidelity guard over the real {@code application.yml}.
 *
 * <p>The behavioural {@link GatewayResilienceIntegrationTest} runs under the
 * {@code test} profile, which wipes {@code spring.cloud.gateway.default-filters}
 * and {@code routes} and re-declares a synthetic policy — so it exercises the
 * Spring Cloud Gateway machinery but gives NO regression protection for the
 * actual production config lines. This test loads the shipped
 * {@code application.yml} directly (no Spring context, no network deps) and
 * pins the resilience-critical values, so a future edit that reverts them is
 * caught in CI.
 *
 * <p>Placeholders (e.g. {@code ${GATEWAY_RESPONSE_TIMEOUT:5s}}) are asserted as
 * their literal text, which pins both the env-var name and the baked-in default.
 */
class GatewayConfigFidelityTest {

    private static Map<String, String> props;

    @BeforeAll
    @SuppressWarnings("unchecked")
    static void loadRealConfig() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        assertThat(sources).as("application.yml should load as a single document").hasSize(1);
        // YamlPropertySourceLoader wraps values in OriginTrackedValue; flatten to
        // plain strings so assertions compare against literal text.
        Map<String, Object> raw = (Map<String, Object>) sources.get(0).getSource();
        props = raw.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> String.valueOf(e.getValue())));
    }

    @Test
    void should_haveRetryDefaultFilter_scopedToGetOnly() {
        assertThat(props.get("spring.cloud.gateway.default-filters[0].name")).isEqualTo("Retry");
        assertThat(props.get("spring.cloud.gateway.default-filters[0].args.methods"))
                .as("POST must not be retried (no idempotency-key support downstream)")
                .isEqualTo("GET");
    }

    @Test
    void should_excludeTimeoutException_fromRetryableExceptions() {
        String prefix = "spring.cloud.gateway.default-filters[0].args.exceptions";
        List<String> exceptionValues = props.entrySet().stream()
                .filter(e -> e.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .toList();

        assertThat(exceptionValues)
                .as("Retry exceptions must be pinned so a response-timeout is not retried")
                .containsExactly("java.io.IOException");
        assertThat(exceptionValues)
                .noneSatisfy(v -> assertThat(v).containsIgnoringCase("timeout"));
    }

    @Test
    void should_defineGlobalResponseTimeout_defaultingTo5s() {
        assertThat(props.get("spring.cloud.gateway.httpclient.response-timeout"))
                .asString()
                .contains("GATEWAY_RESPONSE_TIMEOUT")
                .contains("5s");
    }

    @Test
    void should_disableProgrammaticRouteLocator_byDefault() {
        assertThat(props.get("gateway.programmatic-routes.enabled"))
                .as("declarative routes must be the single authority by default")
                .asString()
                .contains("GATEWAY_PROGRAMMATIC_ROUTES_ENABLED")
                .contains(":false");
    }

    @Test
    void should_overrideResponseTimeout_onBothPaymentRoutes() {
        Pattern idKey = Pattern.compile("spring\\.cloud\\.gateway\\.routes\\[(\\d+)]\\.id");

        List<String> paymentTimeoutKeys = props.entrySet().stream()
                .filter(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    return m.matches() && String.valueOf(e.getValue()).startsWith("payment-service");
                })
                .map(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    m.matches();
                    return "spring.cloud.gateway.routes[" + m.group(1) + "].metadata.response-timeout";
                })
                .filter(props::containsKey)
                .toList();

        // Both the unversioned and /v1 payment routes must carry the override.
        assertThat(paymentTimeoutKeys).hasSize(2);
        paymentTimeoutKeys.forEach(k ->
                assertThat(props.get(k)).asString().contains("GATEWAY_PAYMENT_RESPONSE_TIMEOUT"));
    }

    /**
     * The order-service routes must carry {@code /api/returns/**} alongside
     * {@code /api/orders/**} on both the unversioned and {@code /v1} routes.
     * Without this, admin list calls to {@code GET /api/returns} (and the
     * existing {@code /api/returns/{id}} RMA endpoints) are dropped at the edge.
     * Pinned here so a future edit that reverts the returns path is caught in CI.
     */
    @Test
    void should_routeReturnsPaths_throughOrderService_onBothVersions() {
        Pattern idKey = Pattern.compile("spring\\.cloud\\.gateway\\.routes\\[(\\d+)]\\.id");

        List<String> orderPredicates = props.entrySet().stream()
                .filter(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    return m.matches() && String.valueOf(e.getValue()).startsWith("order-service");
                })
                .map(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    m.matches();
                    return "spring.cloud.gateway.routes[" + m.group(1) + "].predicates[0]";
                })
                .filter(props::containsKey)
                .map(props::get)
                .toList();

        assertThat(orderPredicates).as("order-service routes must be declared").isNotEmpty();
        assertThat(orderPredicates).anySatisfy(p ->
                assertThat(p).contains("/api/orders/**").contains("/api/returns/**"));
        assertThat(orderPredicates).anySatisfy(p ->
                assertThat(p).contains("/api/v1/orders/**").contains("/api/v1/returns/**"));
    }

    /**
     * The admin order list ({@code GET /api/orders}, no path variable) must
     * resolve through the order-service route. Its predicate is
     * {@code /api/orders/**}, and Spring Cloud Gateway's {@link PathPattern}
     * treats a trailing {@code /**} as matching zero-or-more segments — so the
     * bare collection path is served and is NOT shadowed by the
     * {@code /api/orders/{id}} single-order mapping (that distinction is made
     * on the service side by Spring MVC). Pinned so a future edit that narrows
     * the predicate (e.g. to {@code /api/orders/*}) and silently drops the
     * collection request at the edge is caught in CI.
     */
    @Test
    void should_routeBareOrdersCollection_throughOrderService() {
        Pattern idKey = Pattern.compile("spring\\.cloud\\.gateway\\.routes\\[(\\d+)]\\.id");
        PathPatternParser parser = PathPatternParser.defaultInstance;

        boolean collectionMatched = props.entrySet().stream()
                .filter(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    return m.matches() && "order-service".equals(String.valueOf(e.getValue()));
                })
                .map(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    m.matches();
                    return props.get("spring.cloud.gateway.routes[" + m.group(1) + "].predicates[0]");
                })
                .filter(p -> p != null)
                .flatMap(predicate -> Arrays.stream(
                        predicate.replace("Path=", "").split(",")))
                .map(String::trim)
                .anyMatch(pattern -> parser.parse(pattern)
                        .matches(PathContainer.parsePath("/api/orders")));

        assertThat(collectionMatched)
                .as("GET /api/orders (collection) must resolve through the order-service route")
                .isTrue();
    }

    /**
     * The promotion-service routes must carry {@code /api/currency/**}
     * alongside {@code /api/promotions/**} on both the unversioned and
     * {@code /v1} routes. {@code CurrencyController} lives in
     * promotion-service but is mounted at {@code /api/currency}, not under
     * {@code /api/promotions} — without this predicate, admin FX-rate calls
     * (GET /api/currency/rates, POST /api/currency/convert) are dropped at
     * the edge with a 404 despite the service being healthy. Pinned here so
     * a future edit that reverts the currency path is caught in CI.
     */
    @Test
    void should_routeCurrencyPaths_throughPromotionService_onBothVersions() {
        Pattern idKey = Pattern.compile("spring\\.cloud\\.gateway\\.routes\\[(\\d+)]\\.id");

        List<String> promotionPredicates = props.entrySet().stream()
                .filter(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    return m.matches() && String.valueOf(e.getValue()).startsWith("promotion-service");
                })
                .map(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    m.matches();
                    return "spring.cloud.gateway.routes[" + m.group(1) + "].predicates[0]";
                })
                .filter(props::containsKey)
                .map(props::get)
                .toList();

        assertThat(promotionPredicates).as("promotion-service routes must be declared").isNotEmpty();
        assertThat(promotionPredicates).anySatisfy(p ->
                assertThat(p).contains("/api/promotions/**").contains("/api/currency/**"));
        assertThat(promotionPredicates).anySatisfy(p ->
                assertThat(p).contains("/api/v1/promotions/**").contains("/api/v1/currency/**"));
    }
}
