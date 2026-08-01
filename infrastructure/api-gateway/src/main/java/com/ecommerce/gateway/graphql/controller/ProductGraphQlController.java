package com.ecommerce.gateway.graphql.controller;

import com.ecommerce.gateway.graphql.client.ProductBackendClient;
import com.ecommerce.gateway.graphql.client.RecommendationBackendClient;
import com.ecommerce.gateway.graphql.dataloader.ProductDataLoaderRegistrar;
import com.ecommerce.gateway.graphql.dto.PageDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import com.ecommerce.gateway.graphql.dto.RecommendationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dataloader.DataLoader;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Resolvers for {@code Query.product}, {@code Query.products},
 * {@code Query.recommendations} and the nested {@code Product.recommendations}
 * field.
 *
 * <p>The nested resolver intentionally re-uses the {@code productById}
 * DataLoader so when the query asks for both the recommended products' ids
 * and the products themselves the BFF still issues only one batched call to
 * product-service.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ProductGraphQlController {

    private final ProductBackendClient products;
    private final RecommendationBackendClient recommendations;

    @QueryMapping
    public Mono<ProductDto> product(@Argument String id) {
        log.debug("Query.product id={}", id);
        return products.findById(id);
    }

    @QueryMapping
    public Mono<PageDto<ProductDto>> products(
            @Argument String category,
            @Argument Integer page,
            @Argument Integer size,
            @Argument String search) {
        int p = page == null ? 0 : page;
        int s = size == null ? 20 : size;
        log.debug("Query.products category={} search={} page={} size={}", category, search, p, s);
        return products.search(search, category, p, s);
    }

    /**
     * Top-level {@code Query.recommendations} returns hydrated {@link ProductDto}s.
     *
     * <p>We resolve recommendation ids first, then load products through the
     * batched DataLoader — even at the top level, batching wins if the same
     * recommendation set overlaps with another part of the query.
     */
    @QueryMapping
    public Mono<List<ProductDto>> recommendations(
            DataLoader<String, ProductDto> productByIdLoader,
            @Argument String productId,
            @Argument Integer limit) {
        int n = limit == null ? 10 : limit;
        log.debug("Query.recommendations productId={} limit={}", productId, n);
        return recommendations.getForProduct(productId, n)
                .flatMap(list -> hydrate(list, productByIdLoader));
    }

    /**
     * Nested {@code Product.recommendations(limit)}.
     *
     * <p>Spring for GraphQL injects the {@link DataLoader} keyed by the loader
     * name registered in {@link ProductDataLoaderRegistrar}. Method parameter
     * resolution matches by generic type — there is exactly one
     * {@code DataLoader<String, ProductDto>} so this is unambiguous.
     */
    @SchemaMapping(typeName = "Product", field = "recommendations")
    public Mono<List<ProductDto>> productRecommendations(
            ProductDto source,
            DataLoader<String, ProductDto> productByIdLoader,
            @Argument Integer limit) {
        int n = limit == null ? 5 : limit;
        return recommendations.getForProduct(source.id(), n)
                .flatMap(list -> hydrate(list, productByIdLoader));
    }

    private Mono<List<ProductDto>> hydrate(List<RecommendationDto> recs,
                                            DataLoader<String, ProductDto> loader) {
        if (recs == null || recs.isEmpty()) {
            return Mono.just(List.of());
        }
        // DataLoader.loadMany returns CompletableFuture<List<V>> — bridge to Mono.
        return Mono.fromFuture(loader.loadMany(recs.stream().map(RecommendationDto::productId).toList()))
                .flatMapMany(Flux::fromIterable)
                .filter(java.util.Objects::nonNull)
                .collectList();
    }
}
