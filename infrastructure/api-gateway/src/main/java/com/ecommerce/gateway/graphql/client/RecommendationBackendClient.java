package com.ecommerce.gateway.graphql.client;

import com.ecommerce.gateway.graphql.dto.RecommendationDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class RecommendationBackendClient {

    private static final ParameterizedTypeReference<List<RecommendationDto>> LIST_REC =
            new ParameterizedTypeReference<>() {};

    private final WebClient recommendationClient;

    public RecommendationBackendClient(@Qualifier("recommendationClient") WebClient recommendationClient) {
        this.recommendationClient = recommendationClient;
    }

    public Mono<List<RecommendationDto>> getForProduct(String productId, int limit) {
        return recommendationClient.get()
                .uri(uri -> uri.path("/api/recommendations/product/{productId}")
                        .queryParam("limit", limit)
                        .build(productId))
                .retrieve()
                .bodyToMono(LIST_REC)
                .onErrorResume(e -> {
                    log.warn("recommendation-service getForProduct({}) failed: {}", productId, e.toString());
                    return Mono.just(List.of());
                });
    }
}
