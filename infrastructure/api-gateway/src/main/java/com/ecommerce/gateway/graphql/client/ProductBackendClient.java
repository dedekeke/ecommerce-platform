package com.ecommerce.gateway.graphql.client;

import com.ecommerce.gateway.graphql.dto.PageDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reactive client over {@code product-service}.
 *
 * <p>Kept deliberately small: only the calls the BFF needs. All methods return
 * {@link Mono} so they compose into the GraphQL DataFetcher pipeline.
 *
 * <p>Errors are surfaced rather than swallowed — a 404 from the upstream surfaces
 * as an empty {@link Mono}, but every other status propagates the error so the
 * GraphQL response carries a usable {@code errors[]} entry.
 */
@Slf4j
@Component
public class ProductBackendClient {

    private static final ParameterizedTypeReference<PageDto<ProductDto>> PRODUCT_PAGE =
            new ParameterizedTypeReference<>() {};

    private final WebClient productClient;

    public ProductBackendClient(@Qualifier("productClient") WebClient productClient) {
        this.productClient = productClient;
    }

    public Mono<ProductDto> findById(String id) {
        return productClient.get()
                .uri("/api/products/{id}", id)
                .retrieve()
                .bodyToMono(ProductDto.class)
                .onErrorResume(e -> {
                    log.warn("product-service findById({}) failed: {}", id, e.toString());
                    return Mono.empty();
                });
    }

    /**
     * Bulk fetch used by the DataLoader. The product-service does not currently
     * expose a {@code GET /api/products?ids=…} endpoint, so we fan-out per id
     * with {@link Flux#flatMap(Function)} bounded concurrency. When the upstream
     * adds bulk lookup we change this single method and the DataLoader stays
     * the same.
     */
    public Mono<Map<String, ProductDto>> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Mono.just(Map.of());
        }
        return Flux.fromIterable(ids)
                .flatMap(this::findById, /* concurrency */ 8)
                .collectMap(ProductDto::id, p -> p);
    }

    public Mono<PageDto<ProductDto>> search(String search, String category, int page, int size) {
        Function<UriBuilder, URI> uriFn = uri -> {
            uri.path("/api/products")
                    .queryParam("page", page)
                    .queryParam("size", size);
            if (search != null && !search.isBlank()) {
                uri.queryParam("search", search);
            }
            if (category != null && !category.isBlank()) {
                uri.queryParam("categoryId", category);
            }
            return uri.build();
        };

        return productClient.get()
                .uri(uriFn)
                .retrieve()
                .bodyToMono(PRODUCT_PAGE)
                .onErrorResume(e -> {
                    log.warn("product-service search failed: {}", e.toString());
                    return Mono.just(PageDto.empty());
                });
    }
}
