package com.ecommerce.reviewservice.service;

import com.ecommerce.common.exception.BusinessException;
import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.common.exception.UnauthorizedException;
import com.ecommerce.reviewservice.client.OrderServiceClient;
import com.ecommerce.reviewservice.domain.ReviewDocument;
import com.ecommerce.reviewservice.dto.CreateReviewRequest;
import com.ecommerce.reviewservice.dto.ReviewResponse;
import com.ecommerce.reviewservice.dto.ReviewSummaryResponse;
import com.ecommerce.reviewservice.kafka.ReviewEventPublisher;
import com.ecommerce.reviewservice.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Business logic for review CRUD + summary aggregation.
 *
 * <p>Verification of the "verified buyer" badge is delegated to
 * {@link OrderServiceClient}, which fails open (returns false) when
 * order-service is unreachable — see spec.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 5;
    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewRepository reviewRepository;
    private final OrderServiceClient orderServiceClient;
    private final ReviewEventPublisher reviewEventPublisher;

    /**
     * Persist a new review for {@code userId}.
     *
     * <p>Throws {@link BusinessException} (HTTP 409 via the global handler) if
     * the user has already reviewed the product. Throws on invalid rating to
     * complement bean-validation in the controller.
     */
    public ReviewResponse createReview(CreateReviewRequest request, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Authenticated user required to post a review");
        }
        if (request.rating() < MIN_RATING || request.rating() > MAX_RATING) {
            throw new BusinessException("Rating must be between 1 and 5");
        }

        boolean verified = orderServiceClient.hasUserPurchasedProduct(userId, request.productId());

        ReviewDocument doc = ReviewDocument.builder()
                .productId(request.productId())
                .userId(userId)
                .rating(request.rating())
                .title(request.title())
                .body(request.body())
                .verified(verified)
                .helpful(0L)
                .helpfulVoters(new HashSet<>())
                .createdAt(Instant.now())
                .build();

        ReviewDocument saved;
        try {
            saved = reviewRepository.save(doc);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("User has already reviewed this product");
        }

        ReviewSummaryResponse summary = getProductSummary(request.productId());
        reviewEventPublisher.publishReviewCreated(request.productId(), request.rating(), summary);

        log.info("Created review id={} productId={} userId={} verified={}",
                saved.getId(), saved.getProductId(), saved.getUserId(), saved.isVerified());

        return ReviewResponse.from(saved);
    }

    /**
     * Paginated list of reviews for a product, sorted by the chosen mode.
     */
    public Page<ReviewResponse> listProductReviews(String productId, int page, int size, ReviewSortMode sortMode) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(safePage, safeSize, sortMode.sort());

        Page<ReviewDocument> docs = reviewRepository.findByProductId(productId, pageable);
        return docs.map(ReviewResponse::from);
    }

    /**
     * Aggregate stats — average rating, total count, distribution by star.
     *
     * <p>For the volumes typical of a single product (≤ a few thousand reviews)
     * an in-memory reduction is faster than a Mongo aggregation pipeline and
     * doesn't need a dedicated index. If a product ever crosses the
     * 10k-review threshold this should move to a {@code $group} pipeline or a
     * pre-computed counter document.
     */
    public ReviewSummaryResponse getProductSummary(String productId) {
        List<ReviewDocument> all = reviewRepository.findByProductId(productId);
        if (all.isEmpty()) {
            return new ReviewSummaryResponse(0.0, 0, defaultDistribution());
        }

        long total = 0;
        Map<Integer, Long> distribution = defaultDistribution();
        for (ReviewDocument r : all) {
            total += r.getRating();
            distribution.merge(r.getRating(), 1L, Long::sum);
        }
        double avg = (double) total / all.size();
        return new ReviewSummaryResponse(roundToTwoDecimals(avg), all.size(), distribution);
    }

    /**
     * Increments the helpful counter, deduping per user.
     *
     * <p>If {@code userId} has already voted on this review, the call is a
     * no-op and the unchanged review is returned. The counter and the voter
     * set live on the same document, so there is no race window between the
     * dedup check and the increment within a single Mongo write — but two
     * concurrent calls from the same user can still each see no-vote and
     * both increment. We accept that ~at most one extra vote per concurrent
     * burst, since the cost of stronger isolation (e.g. transactions or
     * findAndModify on a $ne match) outweighs the value here.
     */
    public ReviewResponse markHelpful(String reviewId, String userId) {
        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException("Authenticated user required to mark helpful");
        }
        ReviewDocument review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));

        if (review.getHelpfulVoters() == null) {
            review.setHelpfulVoters(new HashSet<>());
        }
        if (review.getHelpfulVoters().contains(userId)) {
            log.debug("User {} already marked review {} as helpful", userId, reviewId);
            return ReviewResponse.from(review);
        }
        review.getHelpfulVoters().add(userId);
        review.setHelpful(review.getHelpful() + 1);
        ReviewDocument updated = reviewRepository.save(review);
        return ReviewResponse.from(updated);
    }

    /**
     * Delete a review. The owner of the review or an admin (callers are
     * pre-authorised at the controller via {@code @PreAuthorize}) may delete.
     */
    public void deleteReview(String reviewId, String requesterUserId, boolean isAdmin) {
        ReviewDocument review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));

        if (!isAdmin && (requesterUserId == null || !requesterUserId.equals(review.getUserId()))) {
            throw new UnauthorizedException("Only the review owner or an admin may delete this review");
        }
        reviewRepository.deleteById(reviewId);
        log.info("Deleted review id={} by requester={} admin={}", reviewId, requesterUserId, isAdmin);
    }

    private static Map<Integer, Long> defaultDistribution() {
        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int i = MIN_RATING; i <= MAX_RATING; i++) {
            distribution.put(i, 0L);
        }
        return distribution;
    }

    private static double roundToTwoDecimals(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
