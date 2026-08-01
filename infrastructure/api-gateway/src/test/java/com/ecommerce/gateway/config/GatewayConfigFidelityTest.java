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

    // ---------- promotion validate: per-IP brute-force guard ----------

    /**
     * {@code POST /api/promotions/validate} stays permitAll (guests apply a
     * promo code before logging in), which makes it a promo-code enumeration
     * oracle. It must therefore be rate limited PER CLIENT IP — the caller is
     * anonymous, so there is no principal to key on — via the same
     * {@code ipKeyResolver} used by the other guest-reachable routes.
     */
    @Test
    void should_rateLimitPromotionValidate_perClientIp_onBothVersions() {
        List<String> keyResolvers = promotionValidateRouteIndexes().stream()
                .map(i -> props.get(rateLimiterArgsPrefix(i) + "key-resolver"))
                .toList();

        assertThat(keyResolvers)
                .as("both the unversioned and /v1 validate routes must be IP-keyed")
                .hasSize(2)
                .allSatisfy(r -> assertThat(r).isEqualTo("#{@ipKeyResolver}"));
    }

    @Test
    void should_bindPromotionValidateLimits_toEnvOverridableVariables() {
        promotionValidateRouteIndexes().forEach(i -> {
            String prefix = rateLimiterArgsPrefix(i) + "redis-rate-limiter.";
            assertThat(props.get(prefix + "replenishRate")).asString().contains("GATEWAY_PROMO_VALIDATE_RATE");
            assertThat(props.get(prefix + "burstCapacity")).asString().contains("GATEWAY_PROMO_VALIDATE_BURST");
        });
    }

    /**
     * The validate ceiling must be materially tighter than the general
     * promotion route's (100/200) — a shopper tries a handful of codes, a
     * scraper tries thousands.
     */
    @Test
    void should_keepPromotionValidateLimit_tighterThanGeneralPromotionRoute() {
        int validateRate = placeholderDefaultAsInt(props.get(
                rateLimiterArgsPrefix(promotionValidateRouteIndexes().get(0))
                        + "redis-rate-limiter.replenishRate"));

        assertThat(validateRate).isLessThan(100);
    }

    @Test
    void should_scopePromotionValidateRoute_toPostOnly() {
        promotionValidateRouteIndexes().forEach(i -> {
            String routePrefix = "spring.cloud.gateway.routes[" + i + "]";
            assertThat(props.get(routePrefix + ".predicates[0]")).asString().contains("promotions/validate");
            assertThat(props.get(routePrefix + ".predicates[1]")).isEqualTo("Method=POST");
        });
    }

    /**
     * Spring Cloud Gateway matches routes in declared order, so the narrow
     * validate route must precede the catch-all {@code /api/promotions/**}
     * route — otherwise it never wins and the tight limit is dead config.
     */
    @Test
    void should_declarePromotionValidateRoute_beforeGeneralPromotionRoute() {
        int firstValidate = promotionValidateRouteIndexes().get(0);
        int firstGeneral = routeIndexesByIdPrefix("promotion-service").get(0);

        assertThat(firstValidate).isLessThan(firstGeneral);
    }

    /**
     * {@code X-Internal-Service-Token} is the service-to-service credential that
     * authorizes {@code POST /api/promotions/apply}. Nothing arriving from the
     * public edge may carry it, so the gateway strips any client-supplied value
     * on every route before forwarding.
     */
    @Test
    void should_stripClientSuppliedInternalServiceTokenHeader() {
        List<String> defaultFilters = props.entrySet().stream()
                .filter(e -> e.getKey().matches("spring\\.cloud\\.gateway\\.default-filters\\[\\d+]"))
                .map(Map.Entry::getValue)
                .toList();

        assertThat(defaultFilters).contains("RemoveRequestHeader=X-Internal-Service-Token");
    }

    private static List<Integer> routeIndexesByIdPrefix(String idPrefix) {
        Pattern idKey = Pattern.compile("spring\\.cloud\\.gateway\\.routes\\[(\\d+)]\\.id");
        return props.entrySet().stream()
                .filter(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    return m.matches() && String.valueOf(e.getValue()).startsWith(idPrefix);
                })
                .map(e -> {
                    Matcher m = idKey.matcher(e.getKey());
                    m.matches();
                    return Integer.parseInt(m.group(1));
                })
                .sorted()
                .toList();
    }

    private static List<Integer> promotionValidateRouteIndexes() {
        List<Integer> indexes = routeIndexesByIdPrefix("promotion-validate");
        assertThat(indexes).as("promotion-validate routes must be declared").isNotEmpty();
        return indexes;
    }

    /**
     * Locates the {@code RequestRateLimiter} entry within a route's filter list
     * (its position differs between the unversioned and /v1 routes, which put
     * RewritePath first) and returns the prefix of its args.
     */
    private static String rateLimiterArgsPrefix(int routeIndex) {
        String routePrefix = "spring.cloud.gateway.routes[" + routeIndex + "].filters[";
        return props.entrySet().stream()
                .filter(e -> e.getKey().startsWith(routePrefix)
                        && e.getKey().endsWith("].name")
                        && "RequestRateLimiter".equals(e.getValue()))
                .map(e -> e.getKey().replace("].name", "].args."))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "route[" + routeIndex + "] has no RequestRateLimiter filter"));
    }

    /** Extracts {@code N} from a {@code ${VAR:N}} placeholder. */
    private static int placeholderDefaultAsInt(String placeholder) {
        Matcher m = Pattern.compile("\\$\\{[^:}]+:(\\d+)}").matcher(String.valueOf(placeholder));
        assertThat(m.find()).as("expected a ${VAR:default} placeholder but was %s", placeholder).isTrue();
        return Integer.parseInt(m.group(1));
    }
}
