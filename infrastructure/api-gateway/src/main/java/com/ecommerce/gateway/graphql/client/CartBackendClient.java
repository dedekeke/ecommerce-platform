package com.ecommerce.gateway.graphql.client;

import com.ecommerce.gateway.graphql.dto.CartDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class CartBackendClient {

    private final WebClient cartClient;

    public CartBackendClient(@Qualifier("cartClient") WebClient cartClient) {
        this.cartClient = cartClient;
    }

    public Mono<CartDto> getCart(String userId) {
        return cartClient.get()
                .uri("/api/cart/{userId}", userId)
                .retrieve()
                .bodyToMono(CartDto.class)
                .onErrorResume(e -> {
                    log.warn("cart-service getCart({}) failed: {}", userId, e.toString());
                    return Mono.empty();
                });
    }
}
