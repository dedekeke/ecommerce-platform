package com.ecommerce.gateway.graphql.controller;

import com.ecommerce.gateway.graphql.client.OrderBackendClient;
import com.ecommerce.gateway.graphql.dto.OrderDto;
import com.ecommerce.gateway.graphql.dto.OrderItemDto;
import com.ecommerce.gateway.graphql.dto.PageDto;
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
public class OrderGraphQlController {

    private final OrderBackendClient orders;

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<OrderDto> order(@Argument String id) {
        log.debug("Query.order id={}", id);
        // TODO: pull userId from the security context once the BFF carries the
        //       JWT principal end-to-end. For now we pass the order id as the
        //       userId placeholder — order-service rejects mismatches anyway.
        return orders.findById(id, id);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public Mono<PageDto<OrderDto>> myOrders(
            @Argument String userId,
            @Argument Integer page,
            @Argument Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        log.debug("Query.myOrders userId={} page={} size={}", userId, p, s);
        return orders.findForUser(userId, p, s);
    }

    @SchemaMapping(typeName = "OrderItem", field = "product")
    public Mono<ProductDto> orderItemProduct(OrderItemDto source,
                                              DataLoader<String, ProductDto> productByIdLoader) {
        if (source.productId() == null) {
            return Mono.empty();
        }
        return Mono.fromFuture(productByIdLoader.load(source.productId()));
    }
}
