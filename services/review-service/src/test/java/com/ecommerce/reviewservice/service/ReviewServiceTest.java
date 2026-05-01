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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository repository;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private ReviewEventPublisher publisher;

    @InjectMocks
    private ReviewService service;

    private CreateReviewRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = new CreateReviewRequest("p1", 5, "Great", "Loved it");
    }

    // ----------------------------------------------------------------------
    // createReview
    // ----------------------------------------------------------------------

    @Test
    void should_persist_review_with_verified_true_when_user_purchased_product() {
        when(orderServiceClient.hasUserPurchasedProduct("user-1", "p1")).thenReturn(true);
        when(repository.save(any(ReviewDocument.class))).thenAnswer(inv -> {
            ReviewDocument d = inv.getArgument(0);
            d.setId("r1");
            return d;
        });
        when(repository.findByProductId("p1")).thenReturn(List.of());

        ReviewResponse response = service.createReview(validRequest, "user-1");

        assertThat(response.id()).isEqualTo("r1");
        assertThat(response.verified()).isTrue();
        assertThat(response.userId()).isEqualTo("user-1");
        assertThat(response.rating()).isEqualTo(5);

        verify(publisher).publishReviewCreated(eq("p1"), eq(5), any(ReviewSummaryResponse.class));
    }

    @Test
    void should_set_verified_false_when_order_service_returns_false() {
        when(orderServiceClient.hasUserPurchasedProduct("user-1", "p1")).thenReturn(false);
        when(repository.save(any(ReviewDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByProductId("p1")).thenReturn(List.of());

        ReviewResponse response = service.createReview(validRequest, "user-1");

        assertThat(response.verified()).isFalse();
    }

    @Test
    void should_reject_when_user_id_missing() {
        assertThatThrownBy(() -> service.createReview(validRequest, null))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(repository, orderServiceClient, publisher);
    }

    @Test
    void should_reject_rating_below_one() {
        CreateReviewRequest bad = new CreateReviewRequest("p1", 0, "x", "x");
        assertThatThrownBy(() -> service.createReview(bad, "user-1"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void should_reject_rating_above_five() {
        CreateReviewRequest bad = new CreateReviewRequest("p1", 6, "x", "x");
        assertThatThrownBy(() -> service.createReview(bad, "user-1"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void should_reject_duplicate_review_per_user_product() {
        when(orderServiceClient.hasUserPurchasedProduct(anyString(), anyString())).thenReturn(true);
        when(repository.save(any(ReviewDocument.class)))
                .thenThrow(new DuplicateKeyException("E11000 dup key"));

        assertThatThrownBy(() -> service.createReview(validRequest, "user-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already reviewed");
        verifyNoInteractions(publisher);
    }

    @Test
    void should_capture_summary_after_create_and_pass_to_publisher() {
        when(orderServiceClient.hasUserPurchasedProduct(anyString(), anyString())).thenReturn(true);
        when(repository.save(any(ReviewDocument.class))).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findByProductId("p1")).thenReturn(List.of(
                build("p1", "u1", 5),
                build("p1", "u2", 4)
        ));

        service.createReview(validRequest, "user-1");

        ArgumentCaptor<ReviewSummaryResponse> captor = ArgumentCaptor.forClass(ReviewSummaryResponse.class);
        verify(publisher).publishReviewCreated(eq("p1"), eq(5), captor.capture());

        ReviewSummaryResponse summary = captor.getValue();
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.averageRating()).isEqualTo(4.5);
    }

    // ----------------------------------------------------------------------
    // listProductReviews
    // ----------------------------------------------------------------------

    @Test
    void should_list_reviews_with_default_helpful_sort() {
        ReviewDocument r1 = build("p1", "u1", 5);
        when(repository.findByProductId(eq("p1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r1)));

        Page<ReviewResponse> result = service.listProductReviews("p1", 0, 20, ReviewSortMode.HELPFUL);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).productId()).isEqualTo("p1");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByProductId(eq("p1"), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("helpful")).isNotNull();
    }

    @Test
    void should_clamp_oversized_page_size() {
        when(repository.findByProductId(eq("p1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listProductReviews("p1", 0, 9999, ReviewSortMode.RECENT);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByProductId(eq("p1"), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    // ----------------------------------------------------------------------
    // getProductSummary
    // ----------------------------------------------------------------------

    @Test
    void should_return_zero_summary_when_no_reviews() {
        when(repository.findByProductId("p1")).thenReturn(List.of());

        ReviewSummaryResponse summary = service.getProductSummary("p1");

        assertThat(summary.count()).isZero();
        assertThat(summary.averageRating()).isEqualTo(0.0);
        assertThat(summary.distribution()).containsKeys(1, 2, 3, 4, 5);
        assertThat(summary.distribution().values()).allMatch(v -> v == 0L);
    }

    @Test
    void should_compute_distribution_and_average_correctly() {
        when(repository.findByProductId("p1")).thenReturn(List.of(
                build("p1", "u1", 5),
                build("p1", "u2", 5),
                build("p1", "u3", 4),
                build("p1", "u4", 1)
        ));

        ReviewSummaryResponse summary = service.getProductSummary("p1");

        assertThat(summary.count()).isEqualTo(4);
        assertThat(summary.averageRating()).isEqualTo(3.75);
        assertThat(summary.distribution().get(5)).isEqualTo(2L);
        assertThat(summary.distribution().get(4)).isEqualTo(1L);
        assertThat(summary.distribution().get(1)).isEqualTo(1L);
        assertThat(summary.distribution().get(2)).isZero();
    }

    // ----------------------------------------------------------------------
    // markHelpful
    // ----------------------------------------------------------------------

    @Test
    void should_increment_helpful_and_record_voter() {
        ReviewDocument doc = build("p1", "u1", 5);
        doc.setId("r1");
        doc.setHelpful(2);
        doc.setHelpfulVoters(new HashSet<>());
        when(repository.findById("r1")).thenReturn(Optional.of(doc));
        when(repository.save(any(ReviewDocument.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = service.markHelpful("r1", "voter-1");

        assertThat(response.helpful()).isEqualTo(3);
        assertThat(doc.getHelpfulVoters()).contains("voter-1");
    }

    @Test
    void should_dedupe_helpful_for_same_user() {
        ReviewDocument doc = build("p1", "u1", 5);
        doc.setId("r1");
        doc.setHelpful(7);
        doc.setHelpfulVoters(new HashSet<>(List.of("voter-1")));
        when(repository.findById("r1")).thenReturn(Optional.of(doc));

        ReviewResponse response = service.markHelpful("r1", "voter-1");

        assertThat(response.helpful()).isEqualTo(7);
        verify(repository, never()).save(any());
    }

    @Test
    void should_throw_not_found_when_marking_helpful_missing_review() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markHelpful("missing", "voter-1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void should_reject_helpful_when_user_id_blank() {
        assertThatThrownBy(() -> service.markHelpful("r1", ""))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ----------------------------------------------------------------------
    // deleteReview
    // ----------------------------------------------------------------------

    @Test
    void should_allow_owner_to_delete_review() {
        ReviewDocument doc = build("p1", "owner-1", 4);
        doc.setId("r1");
        when(repository.findById("r1")).thenReturn(Optional.of(doc));

        service.deleteReview("r1", "owner-1", false);

        verify(repository).deleteById("r1");
    }

    @Test
    void should_allow_admin_to_delete_review() {
        ReviewDocument doc = build("p1", "owner-1", 4);
        doc.setId("r1");
        when(repository.findById("r1")).thenReturn(Optional.of(doc));

        service.deleteReview("r1", "different-user", true);

        verify(repository).deleteById("r1");
    }

    @Test
    void should_reject_delete_by_non_owner_non_admin() {
        ReviewDocument doc = build("p1", "owner-1", 4);
        doc.setId("r1");
        when(repository.findById("r1")).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.deleteReview("r1", "stranger", false))
                .isInstanceOf(UnauthorizedException.class);
        verify(repository, never()).deleteById(anyString());
    }

    @Test
    void should_throw_not_found_when_deleting_missing_review() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteReview("missing", "owner-1", false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ----------------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------------

    private static ReviewDocument build(String productId, String userId, int rating) {
        return ReviewDocument.builder()
                .productId(productId)
                .userId(userId)
                .rating(rating)
                .title("t")
                .body("b")
                .helpful(0)
                .helpfulVoters(new HashSet<>())
                .createdAt(Instant.now())
                .build();
    }
}
