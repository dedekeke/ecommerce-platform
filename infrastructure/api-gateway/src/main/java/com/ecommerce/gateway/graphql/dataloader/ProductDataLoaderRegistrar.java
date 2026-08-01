package com.ecommerce.gateway.graphql.dataloader;

import com.ecommerce.gateway.graphql.client.ProductBackendClient;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.BatchLoaderRegistry;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

/**
 * Registers the {@code "productById"} DataLoader against Spring for GraphQL's
 * {@link BatchLoaderRegistry}.
 *
 * <p><b>Why this matters.</b> Without batching, a query like
 * {@code myOrders { items { product { name } } }} fires one product-service
 * call per OrderItem. With this DataLoader, all unique product ids requested
 * inside a single GraphQL execution are coalesced and dispatched in one
 * {@code findByIds(...)} round-trip.
 *
 * <p>Spring for GraphQL invokes the registered loader once per execution after
 * all field resolvers in the current depth have queued their keys.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ProductDataLoaderRegistrar {

    /**
     * <b>Loader name.</b> Spring for GraphQL's
     * {@code DataLoaderMethodArgumentResolver} resolves the {@link
     * org.dataloader.DataLoader} parameter on a controller method by matching
     * either (a) the method parameter name or (b) the value type's class name.
     * Naming the loader <em>productByIdLoader</em> keeps both controller param
     * names ({@code productByIdLoader}) and the registration name aligned,
     * removing any chance of a silent mismatch when the value DTO is renamed.
     */
    public static final String PRODUCT_LOADER = "productByIdLoader";

    private final BatchLoaderRegistry registry;
    private final ProductBackendClient productClient;

    @PostConstruct
    void register() {
        registry.<String, ProductDto>forName(PRODUCT_LOADER)
                .registerMappedBatchLoader((keys, env) -> {
                    log.debug("DataLoader '{}' batching {} keys: {}", PRODUCT_LOADER, keys.size(), keys);
                    return productClient.findByIds(keys.stream().toList())
                            .switchIfEmpty(Mono.just(java.util.Map.of()));
                });
    }
}
