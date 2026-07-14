package com.ecommerce.gateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
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
}
