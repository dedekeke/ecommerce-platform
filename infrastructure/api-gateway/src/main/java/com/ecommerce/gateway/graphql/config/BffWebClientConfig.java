package com.ecommerce.gateway.graphql.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Per-service {@link WebClient} beans used by the GraphQL DataFetchers.
 *
 * <p>Each client points at a {@code lb://<service-id>} URI so Spring Cloud
 * LoadBalancer resolves the actual instance via Eureka — we reuse the same
 * service registry the gateway already depends on. No new ops surface.
 *
 * <p>The {@link LoadBalanced} {@code WebClient.Builder} is the one piece of
 * Spring Cloud LoadBalancer wiring we need; everything else is plain WebFlux.
 */
@Configuration
public class BffWebClientConfig {

    /**
     * A load-balanced builder. We mark it {@code @Primary}-style by giving it
     * a distinct bean name so we don't collide with any other WebClient.Builder
     * the gateway might define (currently it does not, but defensive).
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder bffWebClientBuilder() {
        return WebClient.builder();
    }

    @Bean(name = "productClient")
    public WebClient productClient(@LoadBalanced WebClient.Builder builder) {
        return builder.baseUrl("lb://product-service").build();
    }

    @Bean(name = "cartClient")
    public WebClient cartClient(@LoadBalanced WebClient.Builder builder) {
        return builder.baseUrl("lb://cart-service").build();
    }

    @Bean(name = "orderClient")
    public WebClient orderClient(@LoadBalanced WebClient.Builder builder) {
        return builder.baseUrl("lb://order-service").build();
    }

    @Bean(name = "recommendationClient")
    public WebClient recommendationClient(@LoadBalanced WebClient.Builder builder) {
        return builder.baseUrl("lb://recommendation-service").build();
    }

    @Bean(name = "promotionClient")
    public WebClient promotionClient(@LoadBalanced WebClient.Builder builder) {
        return builder.baseUrl("lb://promotion-service").build();
    }
}
