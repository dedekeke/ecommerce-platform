package com.ecommerce.reviewservice.controller;

import com.ecommerce.common.exception.ResourceNotFoundException;
import com.ecommerce.reviewservice.dto.CreateReviewRequest;
import com.ecommerce.reviewservice.dto.ReviewResponse;
import com.ecommerce.reviewservice.dto.ReviewSummaryResponse;
import com.ecommerce.reviewservice.service.ReviewService;
import com.ecommerce.reviewservice.service.ReviewSortMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link ReviewController}.
 *
 * <p>We keep filters enabled so {@code .with(jwt())} actually injects an
 * {@code Authentication} into the security context, which is what
 * {@code @AuthenticationPrincipal Jwt} reads. A permissive test
 * {@code SecurityFilterChain} (defined in {@link ReviewControllerTestConfig})
 * lets every request through but still routes through the security chain so
 * the JWT post-processor takes effect.
 */
@WebMvcTest(controllers = ReviewController.class)
@Import({GlobalExceptionHandler.class, ReviewControllerTestConfig.class})
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;

    @MockBean private ReviewService reviewService;

    @Test
    void should_return_201_when_creating_review_with_valid_payload() throws Exception {
        CreateReviewRequest req = new CreateReviewRequest("p1", 5, "Great", "Loved it");
        ReviewResponse resp = new ReviewResponse(
                "r1", "p1", "user-1", 5, "Great", "Loved it", true, 0, Instant.now()
        );
        when(reviewService.createReview(any(), eq("user-1"))).thenReturn(resp);

        mockMvc.perform(post("/api/reviews")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("r1"))
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void should_reject_create_with_invalid_rating() throws Exception {
        CreateReviewRequest bad = new CreateReviewRequest("p1", 6, "x", "y");
        mockMvc.perform(post("/api/reviews")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_reject_create_with_blank_product_id() throws Exception {
        CreateReviewRequest bad = new CreateReviewRequest(" ", 4, "x", "y");
        mockMvc.perform(post("/api/reviews")
                        .with(jwt().jwt(j -> j.subject("user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_list_reviews_for_product_with_pagination_envelope() throws Exception {
        ReviewResponse r1 = new ReviewResponse("r1", "p1", "u1", 5, "t", "b", true, 3, Instant.now());
        when(reviewService.listProductReviews(eq("p1"), anyInt(), anyInt(), any(ReviewSortMode.class)))
                .thenReturn(new PageImpl<>(List.of(r1)));

        mockMvc.perform(get("/api/reviews/product/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value("r1"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.sort").value("helpful"));
    }

    @Test
    @WithMockUser
    void should_use_recent_sort_when_requested() throws Exception {
        when(reviewService.listProductReviews(eq("p1"), anyInt(), anyInt(), eq(ReviewSortMode.RECENT)))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/reviews/product/p1").param("sort", "recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sort").value("recent"));

        verify(reviewService).listProductReviews(eq("p1"), anyInt(), anyInt(), eq(ReviewSortMode.RECENT));
    }

    @Test
    void should_return_summary_for_product() throws Exception {
        when(reviewService.getProductSummary("p1"))
                .thenReturn(new ReviewSummaryResponse(4.5, 10, Map.of(1, 0L, 2, 0L, 3, 1L, 4, 3L, 5, 6L)));

        mockMvc.perform(get("/api/reviews/product/p1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.distribution.5").value(6));
    }

    @Test
    void should_increment_helpful_via_post_endpoint() throws Exception {
        ReviewResponse resp = new ReviewResponse(
                "r1", "p1", "u1", 5, "t", "b", false, 1, Instant.now()
        );
        when(reviewService.markHelpful("r1", "voter-1")).thenReturn(resp);

        mockMvc.perform(post("/api/reviews/r1/helpful")
                        .with(jwt().jwt(j -> j.subject("voter-1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.helpful").value(1));
    }

    @Test
    void should_return_404_when_marking_helpful_on_missing_review() throws Exception {
        when(reviewService.markHelpful(eq("missing"), anyString()))
                .thenThrow(new ResourceNotFoundException("Review", "id", "missing"));

        mockMvc.perform(post("/api/reviews/missing/helpful")
                        .with(jwt().jwt(j -> j.subject("voter-1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void should_return_204_when_owner_deletes_review() throws Exception {
        mockMvc.perform(delete("/api/reviews/r1")
                        .with(jwt().jwt(j -> j.subject("user-1"))))
                .andExpect(status().isNoContent());
        verify(reviewService).deleteReview("r1", "user-1", false);
    }

    @Test
    void should_pass_admin_flag_when_jwt_has_admin_role_claim() throws Exception {
        mockMvc.perform(delete("/api/reviews/r1")
                        .with(jwt().jwt(j -> j.subject("admin-user")
                                .claim("https://ecommerce.com/roles", List.of("admin")))))
                .andExpect(status().isNoContent());
        verify(reviewService).deleteReview("r1", "admin-user", true);
    }
}
