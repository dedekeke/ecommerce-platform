package com.ecommerce.gateway.graphql.controller;

import com.ecommerce.gateway.graphql.client.CartBackendClient;
import com.ecommerce.gateway.graphql.dto.CartDto;
import com.ecommerce.gateway.graphql.dto.CartItemDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Mono;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CartGraphQlController {

    private final CartBackendClient cart;

    /**
     * {@code Query.cart} requires authentication. The expression mirrors the
     * gateway's WebFlux SecurityFilterChain rule: any authenticated principal
     * may load <i>their</i> own cart. The authorization that the {@code userId}
     * argument matches the JWT subject lives downstream in cart-service.
     */
    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<CartDto> cart(@Argument String userId) {
        log.debug("Query.cart userId={}", userId);
        return cart.getCart(userId);
    }

    @SchemaMapping(typeName = "CartItem", field = "product")
    public Mono<ProductDto> cartItemProduct(CartItemDto source,
                                             DataLoader<String, ProductDto> productByIdLoader) {
        if (source.productId() == null) {
            return Mono.empty();
        }
        return Mono.fromFuture(productByIdLoader.load(source.productId()));
    }
}
