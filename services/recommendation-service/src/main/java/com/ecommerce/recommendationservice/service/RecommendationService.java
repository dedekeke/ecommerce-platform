package com.ecommerce.recommendationservice.service;

import com.ecommerce.recommendationservice.domain.CoOccurrenceDocument;
import com.ecommerce.recommendationservice.domain.UserPurchaseDocument;
import com.ecommerce.recommendationservice.dto.RecommendationResponse;
import com.ecommerce.recommendationservice.repository.CoOccurrenceRepository;
import com.ecommerce.recommendationservice.repository.UserPurchaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-side query API for recommendations.
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Per-product</b>: top-N other products that appeared in the same
 *       orders as the given productId. Backed by the
 *       {@code product_count_desc} compound index.</li>
 *   <li><b>Per-user</b>: aggregate co-occurrence across every product the user
 *       has previously bought, exclude products the user already owns, then
 *       return the top-N highest summed scores.</li>
 * </ul>
 *
 * <p>Both endpoints are wrapped in {@link Cacheable} with a 60s TTL configured
 * at the cache-manager level — see {@code application.yml} cache spec.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private final CoOccurrenceRepository coOccurrenceRepository;
    private final UserPurchaseRepository userPurchaseRepository;

    /**
     * Returns up to {@code limit} products most often co-purchased with
     * {@code productId}, sorted by raw count descending.
     */
    @Cacheable(value = "productRecommendations", key = "#productId + ':' + #limit")
    public List<RecommendationResponse> getProductRecommendations(String productId, int limit) {
        int safeLimit = clampLimit(limit);
        log.debug("Fetching top-{} co-occurrent products for productId={}", safeLimit, productId);

        List<CoOccurrenceDocument> rows = coOccurrenceRepository
                .findByProductIdOrderByCountDesc(productId, PageRequest.of(0, safeLimit));

        return rows.stream()
                .map(r -> new RecommendationResponse(r.getOtherProductId(), r.getCount()))
                .toList();
    }

    /**
     * Personalised recommendations for a user: aggregate co-occurrence scores
     * across every product the user owns, exclude already-owned products, and
     * return the top-N.
     */
    @Cacheable(value = "userRecommendations", key = "#userId + ':' + #limit")
    public List<RecommendationResponse> getUserRecommendations(String userId, int limit) {
        int safeLimit = clampLimit(limit);
        log.debug("Computing top-{} personalised recommendations for userId={}", safeLimit, userId);

        UserPurchaseDocument profile = userPurchaseRepository.findById(userId).orElse(null);
        if (profile == null || profile.getProductIds() == null || profile.getProductIds().isEmpty()) {
            log.debug("No purchase history for userId={}, returning empty recommendations", userId);
            return List.of();
        }

        Set<String> owned = profile.getProductIds();
        List<CoOccurrenceDocument> rows = coOccurrenceRepository.findByProductIdIn(List.copyOf(owned));

        Map<String, Long> scoreByProduct = new HashMap<>();
        for (CoOccurrenceDocument row : rows) {
            String candidate = row.getOtherProductId();
            if (candidate == null || owned.contains(candidate)) {
                continue;
            }
            scoreByProduct.merge(candidate, row.getCount(), Long::sum);
        }

        return scoreByProduct.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(safeLimit)
                .map(e -> new RecommendationResponse(e.getKey(), e.getValue()))
                .toList();
    }

    private int clampLimit(int requested) {
        if (requested <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(requested, MAX_LIMIT);
    }
}
