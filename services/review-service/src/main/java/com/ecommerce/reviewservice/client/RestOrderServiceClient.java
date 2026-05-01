package com.ecommerce.reviewservice.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Iterator;

/**
 * Talks to order-service via REST to confirm whether the calling user has a
 * completed order containing the given product. Used to stamp reviews as
 * {@code verified=true}.
 *
 * <p>Fail-open: any exception (network, 5xx, timeout) results in a
 * {@code false} return so a failing order-service can never block a review
 * from being created. The reviewer is simply tagged as unverified in that
 * case, which matches the spec: <i>"if down, set verified=false"</i>.
 */
@Component
@Slf4j
public class RestOrderServiceClient implements OrderServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RestOrderServiceClient(
            RestTemplateBuilder builder,
            @Value("${review.order-service.base-url:http://order-service}") String baseUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(500))
                .setReadTimeout(Duration.ofSeconds(2))
                .build();
        this.baseUrl = baseUrl;
    }

    @Override
    @CircuitBreaker(name = "orderServiceClient", fallbackMethod = "fallbackUnverified")
    @TimeLimiter(name = "orderServiceClient")
    public boolean hasUserPurchasedProduct(String userId, String productId) {
        if (userId == null || userId.isBlank() || productId == null || productId.isBlank()) {
            return false;
        }
        String url = baseUrl + "/api/orders/user/" + userId;
        log.debug("Verifying purchase: GET {}", url);

        JsonNode body = restTemplate.getForObject(url, JsonNode.class);
        if (body == null) {
            return false;
        }
        return containsProduct(body, productId);
    }

    /**
     * order-service returns a list of orders, each with an {@code items} array
     * of {@code {productId, quantity, ...}}. We don't depend on the full DTO
     * shape, just on the existence of any item with the matching productId
     * inside any order with status indicating completion.
     */
    private boolean containsProduct(JsonNode payload, String productId) {
        Iterator<JsonNode> orders = payload.isArray() ? payload.elements() : payload.path("orders").elements();
        while (orders.hasNext()) {
            JsonNode order = orders.next();
            JsonNode status = order.path("status");
            if (!status.isMissingNode() && status.isTextual() && !isCompletedStatus(status.asText())) {
                continue;
            }
            JsonNode items = order.path("items");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    if (productId.equals(item.path("productId").asText(""))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isCompletedStatus(String status) {
        return switch (status.toUpperCase()) {
            case "COMPLETED", "DELIVERED", "PAID", "CONFIRMED" -> true;
            default -> false;
        };
    }

    @SuppressWarnings("unused")
    private boolean fallbackUnverified(String userId, String productId, Throwable t) {
        log.warn("order-service unreachable for user={} product={}, defaulting verified=false: {}",
                userId, productId, t.toString());
        return false;
    }
}
