package com.ecommerce.gateway.graphql.client;

import com.ecommerce.gateway.graphql.dto.OrderDto;
import com.ecommerce.gateway.graphql.dto.PageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class OrderBackendClient {

    private static final ParameterizedTypeReference<PageDto<OrderDto>> ORDER_PAGE =
            new ParameterizedTypeReference<>() {};

    private final WebClient orderClient;

    public OrderBackendClient(@Qualifier("orderClient") WebClient orderClient) {
        this.orderClient = orderClient;
    }

    public Mono<OrderDto> findById(String orderId, String userId) {
        return orderClient.get()
                .uri("/api/orders/{orderId}", orderId)
                .header("X-User-Id", userId)
                .retrieve()
                .bodyToMono(OrderDto.class)
                .onErrorResume(e -> {
                    log.warn("order-service findById({}) failed: {}", orderId, e.toString());
                    return Mono.empty();
                });
    }

    public Mono<PageDto<OrderDto>> findForUser(String userId, int page, int size) {
        return orderClient.get()
                .uri(uri -> uri.path("/api/orders/user/{userId}")
                        .queryParam("page", page)
                        .queryParam("size", size)
                        .build(userId))
                .retrieve()
                .bodyToMono(ORDER_PAGE)
                .onErrorResume(e -> {
                    log.warn("order-service findForUser({}) failed: {}", userId, e.toString());
                    return Mono.just(PageDto.empty());
                });
    }
}
