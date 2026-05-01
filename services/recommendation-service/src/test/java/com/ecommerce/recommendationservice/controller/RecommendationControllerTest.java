package com.ecommerce.recommendationservice.controller;

import com.ecommerce.recommendationservice.dto.RecommendationResponse;
import com.ecommerce.recommendationservice.service.RecommendationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc slice tests for {@link RecommendationController}.
 *
 * <p>Security is disabled via {@code addFilters=false} so we focus on the
 * contract — request mapping, validation, response shape — rather than the
 * Auth0 wiring (covered by integration tests on the security filter chain
 * elsewhere in the platform).
 */
@WebMvcTest(controllers = RecommendationController.class)
@AutoConfigureMockMvc(addFilters = false)
class RecommendationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecommendationService recommendationService;

    @Test
    void should_return_ranked_list_for_GET_product_recommendations() throws Exception {
        when(recommendationService.getProductRecommendations(eq("p1"), eq(10)))
                .thenReturn(List.of(
                        new RecommendationResponse("p2", 10L),
                        new RecommendationResponse("p3", 7L)
                ));

        mockMvc.perform(get("/api/recommendations/product/p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].productId").value("p2"))
                .andExpect(jsonPath("$[0].score").value(10))
                .andExpect(jsonPath("$[1].productId").value("p3"))
                .andExpect(jsonPath("$[1].score").value(7));

        verify(recommendationService).getProductRecommendations("p1", 10);
    }

    @Test
    void should_honour_limit_query_param_for_product_recommendations() throws Exception {
        when(recommendationService.getProductRecommendations(eq("p1"), eq(5)))
                .thenReturn(List.of(new RecommendationResponse("p2", 1L)));

        mockMvc.perform(get("/api/recommendations/product/p1").param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        verify(recommendationService).getProductRecommendations("p1", 5);
    }

    @Test
    void should_reject_limit_below_one_for_product_recommendations() throws Exception {
        mockMvc.perform(get("/api/recommendations/product/p1").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_reject_limit_above_max_for_product_recommendations() throws Exception {
        mockMvc.perform(get("/api/recommendations/product/p1").param("limit", "101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_return_ranked_list_for_GET_user_recommendations() throws Exception {
        when(recommendationService.getUserRecommendations(eq("user-1"), eq(10)))
                .thenReturn(List.of(
                        new RecommendationResponse("p2", 12L),
                        new RecommendationResponse("p3", 5L)
                ));

        mockMvc.perform(get("/api/recommendations/user/user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].productId").value("p2"))
                .andExpect(jsonPath("$[0].score").value(12));

        verify(recommendationService).getUserRecommendations("user-1", 10);
    }

    @Test
    void should_return_empty_list_when_user_has_no_history() throws Exception {
        when(recommendationService.getUserRecommendations(eq("ghost"), eq(10)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/recommendations/user/ghost"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
