package com.ecommerce.promotionservice.controller;

import com.ecommerce.promotionservice.dto.DiscountResult;
import com.ecommerce.promotionservice.dto.PromotionRequest;
import com.ecommerce.promotionservice.dto.PromotionResponse;
import com.ecommerce.promotionservice.dto.PromotionValidationRequest;
import com.ecommerce.promotionservice.model.PromotionType;
import com.ecommerce.promotionservice.security.InternalServiceTokenFilter;
import com.ecommerce.promotionservice.service.PromotionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization tests for the promotion write endpoints.
 *
 * Boots the real Spring Security filter chain ({@code security.enabled=true}) so
 * the JWT-scope authorization is exercised end to end. The mutating endpoints
 * (POST/PUT/DELETE) must require {@code SCOPE_admin}; the read + validate
 * endpoints stay anonymous; {@code POST /apply} is restricted to service
 * callers presenting the internal service token. The JwtDecoder is mocked so
 * the resource server starts without contacting Auth0; the caller identity is
 * supplied via the {@code jwt()} post-processor (the {@code scope} claim maps
 * to {@code SCOPE_*} authorities via the default converter).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "security.internal.service-token=" + PromotionControllerSecurityTest.SERVICE_TOKEN,
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/",
        "grpc.server.port=-1"
})
@DisplayName("Promotion Controller Authorization Tests")
class PromotionControllerSecurityTest {

    static final String SERVICE_TOKEN = "test-internal-service-token";

    private static final SimpleGrantedAuthority ADMIN = new SimpleGrantedAuthority("SCOPE_admin");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PromotionService promotionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private String promotionRequestJson() throws Exception {
        PromotionRequest request = PromotionRequest.builder()
                .code("SAVE20")
                .name("20% Off Sale")
                .type(PromotionType.PERCENTAGE)
                .discountValue(BigDecimal.valueOf(20))
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(1))
                .build();
        return objectMapper.writeValueAsString(request);
    }

    private String validationRequestJson() throws Exception {
        PromotionValidationRequest request = PromotionValidationRequest.builder()
                .code("SAVE20")
                .purchaseAmount(BigDecimal.valueOf(200))
                .build();
        return objectMapper.writeValueAsString(request);
    }

    // ---------- POST /api/promotions ----------

    @Test
    @DisplayName("should_return401_when_createPromotionUnauthenticated")
    void should_return401_when_createPromotionUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promotionRequestJson()))
                .andExpect(status().isUnauthorized());

        verify(promotionService, never()).createPromotion(any());
    }

    @Test
    @DisplayName("should_return403_when_createPromotionWithoutAdminScope")
    void should_return403_when_createPromotionWithoutAdminScope() throws Exception {
        mockMvc.perform(post("/api/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promotionRequestJson())
                        .with(jwt().jwt(jwt -> jwt.claim("scope", "read:promotions"))))
                .andExpect(status().isForbidden());

        verify(promotionService, never()).createPromotion(any());
    }

    @Test
    @DisplayName("should_return201_when_createPromotionWithAdminScope")
    void should_return201_when_createPromotionWithAdminScope() throws Exception {
        when(promotionService.createPromotion(any())).thenReturn(PromotionResponse.builder().id(1L).build());

        mockMvc.perform(post("/api/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promotionRequestJson())
                        .with(jwt().authorities(ADMIN)))
                .andExpect(status().isCreated());

        verify(promotionService).createPromotion(any());
    }

    // ---------- PUT /api/promotions/{id} ----------

    @Test
    @DisplayName("should_return401_when_updatePromotionUnauthenticated")
    void should_return401_when_updatePromotionUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/promotions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promotionRequestJson()))
                .andExpect(status().isUnauthorized());

        verify(promotionService, never()).updatePromotion(anyLong(), any());
    }

    @Test
    @DisplayName("should_return403_when_updatePromotionWithoutAdminScope")
    void should_return403_when_updatePromotionWithoutAdminScope() throws Exception {
        mockMvc.perform(put("/api/promotions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promotionRequestJson())
                        .with(jwt().jwt(jwt -> jwt.claim("scope", "read:promotions"))))
                .andExpect(status().isForbidden());

        verify(promotionService, never()).updatePromotion(anyLong(), any());
    }

    @Test
    @DisplayName("should_return200_when_updatePromotionWithAdminScope")
    void should_return200_when_updatePromotionWithAdminScope() throws Exception {
        when(promotionService.updatePromotion(anyLong(), any()))
                .thenReturn(PromotionResponse.builder().id(1L).build());

        mockMvc.perform(put("/api/promotions/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(promotionRequestJson())
                        .with(jwt().authorities(ADMIN)))
                .andExpect(status().isOk());

        verify(promotionService).updatePromotion(anyLong(), any());
    }

    // ---------- DELETE /api/promotions/{id} ----------

    @Test
    @DisplayName("should_return401_when_deletePromotionUnauthenticated")
    void should_return401_when_deletePromotionUnauthenticated() throws Exception {
        mockMvc.perform(delete("/api/promotions/1"))
                .andExpect(status().isUnauthorized());

        verify(promotionService, never()).deletePromotion(anyLong());
    }

    @Test
    @DisplayName("should_return403_when_deletePromotionWithoutAdminScope")
    void should_return403_when_deletePromotionWithoutAdminScope() throws Exception {
        mockMvc.perform(delete("/api/promotions/1")
                        .with(jwt().jwt(jwt -> jwt.claim("scope", "read:promotions"))))
                .andExpect(status().isForbidden());

        verify(promotionService, never()).deletePromotion(anyLong());
    }

    @Test
    @DisplayName("should_return204_when_deletePromotionWithAdminScope")
    void should_return204_when_deletePromotionWithAdminScope() throws Exception {
        mockMvc.perform(delete("/api/promotions/1")
                        .with(jwt().authorities(ADMIN)))
                .andExpect(status().isNoContent());

        verify(promotionService).deletePromotion(1L);
    }

    // ---------- Anonymous (read + validate) still open ----------

    @Test
    @DisplayName("should_allowAnonymous_when_getAllActivePromotions")
    void should_allowAnonymous_when_getAllActivePromotions() throws Exception {
        when(promotionService.getAllActivePromotions()).thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/promotions"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_allowAnonymous_when_validatePromotion")
    void should_allowAnonymous_when_validatePromotion() throws Exception {
        when(promotionService.validatePromotion(any()))
                .thenReturn(DiscountResult.builder().valid(true).build());

        mockMvc.perform(post("/api/promotions/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("should_allowAnonymous_when_validatePromotionWithoutServiceToken")
    void should_allowAnonymous_when_validatePromotionWithoutServiceToken() throws Exception {
        when(promotionService.validatePromotion(any()))
                .thenReturn(DiscountResult.builder().valid(true).build());

        mockMvc.perform(post("/api/promotions/validate")
                        .header(InternalServiceTokenFilter.HEADER_NAME, "not-the-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson()))
                .andExpect(status().isOk());
    }

    // ---------- POST /api/promotions/apply (service callers only) ----------

    @Test
    @DisplayName("should_return401_when_applyPromotionUnauthenticated")
    void should_return401_when_applyPromotionUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/promotions/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson()))
                .andExpect(status().isUnauthorized());

        verify(promotionService, never()).applyPromotion(any());
    }

    @Test
    @DisplayName("should_return401_when_applyPromotionWithWrongServiceToken")
    void should_return401_when_applyPromotionWithWrongServiceToken() throws Exception {
        mockMvc.perform(post("/api/promotions/apply")
                        .header(InternalServiceTokenFilter.HEADER_NAME, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson()))
                .andExpect(status().isUnauthorized());

        verify(promotionService, never()).applyPromotion(any());
    }

    /**
     * An end-user JWT — the credential a browser could obtain — must not be
     * enough: /apply is a service-to-service operation, not a user operation.
     */
    @Test
    @DisplayName("should_return403_when_applyPromotionWithEndUserJwt")
    void should_return403_when_applyPromotionWithEndUserJwt() throws Exception {
        mockMvc.perform(post("/api/promotions/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson())
                        .with(jwt().jwt(jwt -> jwt.claim("scope", "read:promotions"))))
                .andExpect(status().isForbidden());

        verify(promotionService, never()).applyPromotion(any());
    }

    @Test
    @DisplayName("should_return403_when_applyPromotionWithAdminScopeOnly")
    void should_return403_when_applyPromotionWithAdminScopeOnly() throws Exception {
        mockMvc.perform(post("/api/promotions/apply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson())
                        .with(jwt().authorities(ADMIN)))
                .andExpect(status().isForbidden());

        verify(promotionService, never()).applyPromotion(any());
    }

    @Test
    @DisplayName("should_return200_when_applyPromotionWithServiceToken")
    void should_return200_when_applyPromotionWithServiceToken() throws Exception {
        when(promotionService.applyPromotion(any()))
                .thenReturn(DiscountResult.builder().valid(true).build());

        mockMvc.perform(post("/api/promotions/apply")
                        .header(InternalServiceTokenFilter.HEADER_NAME, SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson()))
                .andExpect(status().isOk());

        verify(promotionService).applyPromotion(any());
    }

    /**
     * The guest checkout path carries no user JWT at all — the service token
     * alone must authorize it, exactly as on the authenticated path.
     */
    @Test
    @DisplayName("should_return200_when_applyPromotionWithServiceTokenAndUserJwt")
    void should_return200_when_applyPromotionWithServiceTokenAndUserJwt() throws Exception {
        when(promotionService.applyPromotion(any()))
                .thenReturn(DiscountResult.builder().valid(true).build());

        mockMvc.perform(post("/api/promotions/apply")
                        .header(InternalServiceTokenFilter.HEADER_NAME, SERVICE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson())
                        .with(jwt().jwt(jwt -> jwt.claim("scope", "read:promotions"))))
                .andExpect(status().isOk());

        verify(promotionService).applyPromotion(any());
    }

    // ---------- Filter ordering: real chain, REAL Authorization headers ----------
    // These drive an actual "Authorization: Bearer ..." header through the real
    // filter chain (BearerTokenAuthenticationFilter included). The jwt()
    // post-processor cannot prove this: it pre-populates the SecurityContext and
    // never exercises the bearer filter, which is exactly where the ordering
    // landmine lives.

    /**
     * The landmine: {@code BearerTokenAuthenticationFilter} 401s a request the
     * instant an {@code Authorization: Bearer} header fails to decode. If the
     * service-token filter ran after it, a service call that happened to carry a
     * stale/garbage user JWT would be rejected despite presenting a VALID
     * service credential. The resolver must suppress bearer processing for
     * trusted service calls so the service credential always wins.
     */
    @Test
    @DisplayName("should_return200_when_applyPromotionWithServiceTokenAndInvalidBearerHeader")
    void should_return200_when_applyPromotionWithServiceTokenAndInvalidBearerHeader() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("expired or malformed"));
        when(promotionService.applyPromotion(any()))
                .thenReturn(DiscountResult.builder().valid(true).build());

        mockMvc.perform(post("/api/promotions/apply")
                        .header(InternalServiceTokenFilter.HEADER_NAME, SERVICE_TOKEN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson()))
                .andExpect(status().isOk());

        verify(promotionService).applyPromotion(any());
    }

    /**
     * The suppression is scoped to trusted service calls only: WITHOUT a valid
     * service token, a bad bearer token keeps the standard 401 semantics.
     */
    @Test
    @DisplayName("should_return401_when_applyPromotionWithInvalidBearerHeaderOnly")
    void should_return401_when_applyPromotionWithInvalidBearerHeaderOnly() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("expired or malformed"));

        mockMvc.perform(post("/api/promotions/apply")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson()))
                .andExpect(status().isUnauthorized());

        verify(promotionService, never()).applyPromotion(any());
    }

    /**
     * A WRONG service token must not buy the bearer suppression either — the
     * invalid bearer token still 401s.
     */
    @Test
    @DisplayName("should_return401_when_applyPromotionWithWrongServiceTokenAndInvalidBearerHeader")
    void should_return401_when_applyPromotionWithWrongServiceTokenAndInvalidBearerHeader() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("expired or malformed"));

        mockMvc.perform(post("/api/promotions/apply")
                        .header(InternalServiceTokenFilter.HEADER_NAME, "wrong-token")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson()))
                .andExpect(status().isUnauthorized());

        verify(promotionService, never()).applyPromotion(any());
    }

    /**
     * The bearer suppression is uniform across the service's endpoints, not
     * special-cased for /apply: a trusted service call to the public /validate
     * path is likewise unaffected by an unusable bearer header.
     */
    @Test
    @DisplayName("should_return200_when_validatePromotionWithServiceTokenAndInvalidBearerHeader")
    void should_return200_when_validatePromotionWithServiceTokenAndInvalidBearerHeader() throws Exception {
        when(jwtDecoder.decode(anyString())).thenThrow(new BadJwtException("expired or malformed"));
        when(promotionService.validatePromotion(any()))
                .thenReturn(DiscountResult.builder().valid(true).build());

        mockMvc.perform(post("/api/promotions/validate")
                        .header(InternalServiceTokenFilter.HEADER_NAME, SERVICE_TOKEN)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer garbage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validationRequestJson()))
                .andExpect(status().isOk());
    }
}
