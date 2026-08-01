package com.ecommerce.recommendationservice.service;

import com.ecommerce.recommendationservice.domain.CoOccurrenceDocument;
import com.ecommerce.recommendationservice.domain.UserPurchaseDocument;
import com.ecommerce.recommendationservice.dto.RecommendationResponse;
import com.ecommerce.recommendationservice.repository.CoOccurrenceRepository;
import com.ecommerce.recommendationservice.repository.UserPurchaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private CoOccurrenceRepository coOccurrenceRepository;

    @Mock
    private UserPurchaseRepository userPurchaseRepository;

    @InjectMocks
    private RecommendationService recommendationService;

    private static CoOccurrenceDocument row(String productId, String otherProductId, long count) {
        return CoOccurrenceDocument.builder()
                .id(CoOccurrenceDocument.buildId(productId, otherProductId))
                .productId(productId)
                .otherProductId(otherProductId)
                .count(count)
                .build();
    }

    @Test
    void should_return_top_n_co_occurrent_products_for_a_given_product() {
        when(coOccurrenceRepository.findByProductIdOrderByCountDesc(eq("p1"), any(Pageable.class)))
                .thenReturn(List.of(
                        row("p1", "p2", 10),
                        row("p1", "p3", 7),
                        row("p1", "p4", 3)
                ));

        List<RecommendationResponse> results = recommendationService.getProductRecommendations("p1", 10);

        assertThat(results).extracting(RecommendationResponse::productId)
                .containsExactly("p2", "p3", "p4");
        assertThat(results).extracting(RecommendationResponse::score)
                .containsExactly(10L, 7L, 3L);
    }

    @Test
    void should_aggregate_user_recommendations_and_exclude_owned_products() {
        Set<String> owned = new HashSet<>(List.of("p1", "p2"));
        when(userPurchaseRepository.findById("user-1"))
                .thenReturn(Optional.of(UserPurchaseDocument.builder()
                        .userId("user-1")
                        .productIds(owned)
                        .build()));

        // p1 co-occurs with p2 (owned, excluded), p3 (5), p4 (2)
        // p2 co-occurs with p1 (owned, excluded), p3 (4), p5 (8)
        // Aggregated: p3=9, p4=2, p5=8 → sorted desc: p3 (9), p5 (8), p4 (2)
        when(coOccurrenceRepository.findByProductIdIn(any()))
                .thenReturn(List.of(
                        row("p1", "p2", 10),
                        row("p1", "p3", 5),
                        row("p1", "p4", 2),
                        row("p2", "p1", 10),
                        row("p2", "p3", 4),
                        row("p2", "p5", 8)
                ));

        List<RecommendationResponse> results = recommendationService.getUserRecommendations("user-1", 10);

        assertThat(results).extracting(RecommendationResponse::productId)
                .containsExactly("p3", "p5", "p4");
        assertThat(results).extracting(RecommendationResponse::score)
                .containsExactly(9L, 8L, 2L);
    }

    @Test
    void should_return_empty_list_when_user_has_no_purchase_history() {
        when(userPurchaseRepository.findById("ghost")).thenReturn(Optional.empty());

        List<RecommendationResponse> results = recommendationService.getUserRecommendations("ghost", 10);

        assertThat(results).isEmpty();
    }

    @Test
    void should_return_empty_list_when_user_purchase_set_is_empty() {
        when(userPurchaseRepository.findById("user-1"))
                .thenReturn(Optional.of(UserPurchaseDocument.builder()
                        .userId("user-1")
                        .productIds(new HashSet<>())
                        .build()));

        List<RecommendationResponse> results = recommendationService.getUserRecommendations("user-1", 10);

        assertThat(results).isEmpty();
    }

    @Test
    void should_clamp_negative_or_zero_limit_to_default() {
        when(coOccurrenceRepository.findByProductIdOrderByCountDesc(eq("p1"), any(Pageable.class)))
                .thenReturn(List.of(row("p1", "p2", 5)));

        List<RecommendationResponse> results = recommendationService.getProductRecommendations("p1", 0);

        assertThat(results).hasSize(1);
    }

    @Test
    void should_honour_user_limit_after_aggregation() {
        when(userPurchaseRepository.findById("user-1"))
                .thenReturn(Optional.of(UserPurchaseDocument.builder()
                        .userId("user-1")
                        .productIds(new HashSet<>(List.of("p1")))
                        .build()));
        when(coOccurrenceRepository.findByProductIdIn(any()))
                .thenReturn(List.of(
                        row("p1", "p2", 9),
                        row("p1", "p3", 8),
                        row("p1", "p4", 7)
                ));

        List<RecommendationResponse> results = recommendationService.getUserRecommendations("user-1", 2);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(RecommendationResponse::productId).containsExactly("p2", "p3");
    }
}
