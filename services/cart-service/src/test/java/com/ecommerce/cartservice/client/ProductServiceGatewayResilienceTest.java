package com.ecommerce.cartservice.client;

import com.ecommerce.cartservice.dto.ProductDto;
import com.ecommerce.cartservice.exception.ProductServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot3.bulkhead.autoconfigure.BulkheadAutoConfiguration;
import io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies the Resilience4j wiring on {@link ProductServiceGateway} using the
 * real circuit-breaker aspect (Spring AOP) against a Mockito-mocked Feign
 * client. Infra autoconfig (JDBC/JPA/Redis/Kafka/Eureka/Security) is excluded so
 * this suite needs no Docker and boots in milliseconds.
 *
 * <p>Breaker thresholds are overridden here (small COUNT_BASED window, long
 * open state) purely for deterministic, fast assertions — production values
 * live in application.yml.
 */
@SpringBootTest(
        classes = ProductServiceGatewayResilienceTest.TestApp.class,
        properties = {
                "spring.cloud.config.enabled=false",
                // Base config (recordExceptions/ignoreExceptions) is inherited from
                // application.yml; only the window is tightened for fast, deterministic
                // assertions and the open state is held long enough to assert fail-fast.
                "resilience4j.circuitbreaker.instances.product-service.slidingWindowType=COUNT_BASED",
                "resilience4j.circuitbreaker.instances.product-service.slidingWindowSize=5",
                "resilience4j.circuitbreaker.instances.product-service.minimumNumberOfCalls=5",
                "resilience4j.circuitbreaker.instances.product-service.failureRateThreshold=50",
                "resilience4j.circuitbreaker.instances.product-service.waitDurationInOpenState=30s",
                "resilience4j.circuitbreaker.instances.product-service.permittedNumberOfCallsInHalfOpenState=2",
                "resilience4j.circuitbreaker.instances.product-service.automaticTransitionFromOpenToHalfOpenEnabled=false",
                // single permit + no wait so the saturation path is deterministic
                "resilience4j.bulkhead.instances.product-service.maxConcurrentCalls=1",
                "resilience4j.bulkhead.instances.product-service.maxWaitDuration=0ms"
        }
)
class ProductServiceGatewayResilienceTest {

    /**
     * Minimal context: only AOP + Resilience4j (circuit breaker + bulkhead)
     * autoconfig plus the gateway and a mocked Feign client. No JDBC, JPA,
     * Kafka, Redis, Eureka or gRPC — so the suite needs no Docker/infra.
     */
    @Configuration
    @ImportAutoConfiguration({
            AopAutoConfiguration.class,
            CircuitBreakerAutoConfiguration.class,
            BulkheadAutoConfiguration.class
    })
    @Import(ProductServiceGateway.class)
    static class TestApp {
        @Bean
        ProductServiceClient productServiceClient() {
            return mock(ProductServiceClient.class);
        }
    }

    @Autowired
    private ProductServiceGateway gateway;

    @Autowired
    private ProductServiceClient productServiceClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker breaker;

    private static final String PRODUCT_ID = "product-001";

    @BeforeEach
    void setUp() {
        reset(productServiceClient);
        breaker = circuitBreakerRegistry.circuitBreaker("product-service");
        breaker.reset();
    }

    /** Real FeignException of the given HTTP status (errorStatus picks the right subtype). */
    private static FeignException feignException(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET, "http://product-service/api/products/" + PRODUCT_ID,
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder()
                .status(status)
                .reason("simulated " + status)
                .request(request)
                .headers(Collections.emptyMap())
                .build();
        return FeignException.errorStatus("getProductById", response);
    }

    private ProductDto validProduct() {
        return ProductDto.builder()
                .id(PRODUCT_ID)
                .name("Test Product")
                .price(new BigDecimal("29.99"))
                .stockQuantity(10)
                .active(true)
                .build();
    }

    private void driveBreakerOpen() {
        when(productServiceClient.getProductById(PRODUCT_ID)).thenThrow(feignException(503));
        for (int i = 0; i < 5; i++) {
            try {
                gateway.getProductById(PRODUCT_ID);
            } catch (ProductServiceUnavailableException ignored) {
                // expected — fallback fires on each failing call
            }
        }
    }

    @Test
    @DisplayName("happy path is unaffected by the resilience wrapper")
    void should_returnProduct_when_backendHealthy() {
        when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(validProduct());

        ProductDto result = gateway.getProductById(PRODUCT_ID);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(PRODUCT_ID);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("circuit opens after the configured number of failures")
    void should_openCircuit_when_failureThresholdExceeded() {
        driveBreakerOpen();

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("calls fail fast without touching the backend once the circuit is open")
    void should_failFast_when_circuitOpen() {
        driveBreakerOpen();
        clearInvocations(productServiceClient);

        long start = System.nanoTime();
        assertThatThrownBy(() -> gateway.getProductById(PRODUCT_ID))
                .isInstanceOf(ProductServiceUnavailableException.class);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        // Short-circuited: the downstream client is never invoked and there is
        // no read-timeout wait.
        verify(productServiceClient, never()).getProductById(anyString());
        assertThat(elapsedMs).isLessThan(500);
    }

    @Test
    @DisplayName("half-open probe success closes the circuit again")
    void should_closeCircuit_when_halfOpenProbesSucceed() {
        driveBreakerOpen();
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        breaker.transitionToHalfOpenState();
        // doReturn (not when/thenReturn) — the method is already stubbed to throw,
        // so evaluating it inside when(...) would raise the stubbed exception.
        doReturn(validProduct()).when(productServiceClient).getProductById(PRODUCT_ID);

        // permittedNumberOfCallsInHalfOpenState = 2
        gateway.getProductById(PRODUCT_ID);
        gateway.getProductById(PRODUCT_ID);

        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("fallback surfaces a typed ProductServiceUnavailableException on backend 5xx")
    void should_throwProductServiceUnavailable_when_backendReturns5xx() {
        when(productServiceClient.getProductById(PRODUCT_ID)).thenThrow(feignException(503));

        assertThatThrownBy(() -> gateway.getProductById(PRODUCT_ID))
                .isInstanceOf(ProductServiceUnavailableException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    @DisplayName("a genuine 4xx is re-thrown as-is and does not trip the breaker")
    void should_rethrow4xx_and_notTripBreaker() {
        when(productServiceClient.getProductById(PRODUCT_ID)).thenThrow(feignException(404));

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> gateway.getProductById(PRODUCT_ID))
                    .isInstanceOf(FeignException.NotFound.class)
                    .isNotInstanceOf(ProductServiceUnavailableException.class);
        }

        // 4xx is a legitimate product-service response, not an outage.
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("bulkhead saturation is recorded as a circuit-breaker FAILURE (not a success)")
    void should_recordBulkheadFullAsBreakerFailure() throws InterruptedException {
        // Regression guard for the record-all vs allow-list semantics bug:
        // @CircuitBreaker wraps @Bulkhead, so BulkheadFullException must count as
        // a CB failure, otherwise the breaker can never open on saturation.
        CountDownLatch permitHeld = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(productServiceClient.getProductById(PRODUCT_ID)).thenAnswer(invocation -> {
            permitHeld.countDown();
            release.await(2, TimeUnit.SECONDS);
            return validProduct();
        });

        Thread holder = new Thread(() -> {
            try {
                gateway.getProductById(PRODUCT_ID);
            } catch (RuntimeException ignored) {
                // not relevant to this test
            }
        });
        holder.start();
        assertThat(permitHeld.await(2, TimeUnit.SECONDS))
                .as("holder thread should occupy the only bulkhead permit")
                .isTrue();

        int failuresBefore = breaker.getMetrics().getNumberOfFailedCalls();

        // Second concurrent call: no permit, no wait -> rejected and failed fast.
        assertThatThrownBy(() -> gateway.getProductById(PRODUCT_ID))
                .isInstanceOf(ProductServiceUnavailableException.class);

        int failuresAfter = breaker.getMetrics().getNumberOfFailedCalls();
        assertThat(failuresAfter)
                .as("bulkhead rejection must be tallied as a breaker failure")
                .isGreaterThan(failuresBefore);

        release.countDown();
        holder.join(2000);
    }
}
