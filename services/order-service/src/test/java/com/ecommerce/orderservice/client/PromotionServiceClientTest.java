package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.client.dto.DiscountResult;
import com.ecommerce.orderservice.client.dto.LoyaltyResult;
import com.ecommerce.orderservice.client.dto.PromotionValidationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PromotionServiceClient Tests")
class PromotionServiceClientTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    private PromotionServiceClient promotionServiceClient;

    private static final String PROMOTION_SERVICE_URL = "http://localhost:8090";
    private static final String INTERNAL_TOKEN = "internal-service-token";

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.defaultHeader(anyString(), any(String[].class)))
            .thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        promotionServiceClient = new PromotionServiceClient(
            restClientBuilder, PROMOTION_SERVICE_URL, INTERNAL_TOKEN);
    }

    // ==================== Service credential ====================

    /**
     * The saga applies promotions on the GUEST checkout path, where there is no
     * user JWT — so the caller's own service credential, sent on every call, is
     * what authorizes the (service-only) /apply endpoint.
     */
    @Test
    @DisplayName("should_sendInternalServiceToken_when_tokenConfigured")
    void should_sendInternalServiceToken_when_tokenConfigured() {
        verify(restClientBuilder).defaultHeader(
            PromotionServiceClient.INTERNAL_TOKEN_HEADER, INTERNAL_TOKEN);
    }

    @Test
    @DisplayName("should_omitInternalServiceToken_when_tokenBlank")
    void should_omitInternalServiceToken_when_tokenBlank() {
        clearInvocations(restClientBuilder);

        new PromotionServiceClient(restClientBuilder, PROMOTION_SERVICE_URL, "  ");

        verify(restClientBuilder, never()).defaultHeader(
            eq(PromotionServiceClient.INTERNAL_TOKEN_HEADER), any(String[].class));
    }

    @Test
    @DisplayName("Should validate promotion successfully")
    void shouldValidatePromotionSuccessfully() {
        // Given
        PromotionValidationRequest request = PromotionValidationRequest.builder()
            .code("SAVE20")
            .purchaseAmount(BigDecimal.valueOf(100.00))
            .build();

        DiscountResult expectedResult = DiscountResult.builder()
            .valid(true)
            .message("Promotion applied successfully")
            .discountAmount(BigDecimal.valueOf(20.00))
            .finalAmount(BigDecimal.valueOf(80.00))
            .promotionCode("SAVE20")
            .promotionName("20% Off")
            .build();

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(PromotionValidationRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(DiscountResult.class)).thenReturn(expectedResult);

        // When
        DiscountResult result = promotionServiceClient.validatePromotion(request);

        // Then
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals("Promotion applied successfully", result.getMessage());
        assertEquals(BigDecimal.valueOf(20.00), result.getDiscountAmount());
        assertEquals(BigDecimal.valueOf(80.00), result.getFinalAmount());
        assertEquals("SAVE20", result.getPromotionCode());
    }

    @Test
    @DisplayName("Should return invalid result when promotion code is invalid")
    void shouldReturnInvalidResultWhenPromotionCodeIsInvalid() {
        // Given
        PromotionValidationRequest request = PromotionValidationRequest.builder()
            .code("INVALID")
            .purchaseAmount(BigDecimal.valueOf(100.00))
            .build();

        DiscountResult expectedResult = DiscountResult.builder()
            .valid(false)
            .message("Promotion code not found")
            .discountAmount(BigDecimal.ZERO)
            .finalAmount(BigDecimal.valueOf(100.00))
            .build();

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(PromotionValidationRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(DiscountResult.class)).thenReturn(expectedResult);

        // When
        DiscountResult result = promotionServiceClient.validatePromotion(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals("Promotion code not found", result.getMessage());
        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
    }

    @Test
    @DisplayName("Should return invalid result when purchase amount is below minimum")
    void shouldReturnInvalidResultWhenPurchaseAmountBelowMinimum() {
        // Given
        PromotionValidationRequest request = PromotionValidationRequest.builder()
            .code("SAVE20")
            .purchaseAmount(BigDecimal.valueOf(50.00))
            .build();

        DiscountResult expectedResult = DiscountResult.builder()
            .valid(false)
            .message("Purchase amount must be at least $100")
            .discountAmount(BigDecimal.ZERO)
            .finalAmount(BigDecimal.valueOf(50.00))
            .build();

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(PromotionValidationRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(DiscountResult.class)).thenReturn(expectedResult);

        // When
        DiscountResult result = promotionServiceClient.validatePromotion(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("at least"));
        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
    }

    @Test
    @DisplayName("Should handle service unavailable gracefully")
    void shouldHandleServiceUnavailableGracefully() {
        // Given
        PromotionValidationRequest request = PromotionValidationRequest.builder()
            .code("SAVE20")
            .purchaseAmount(BigDecimal.valueOf(100.00))
            .build();

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(PromotionValidationRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(DiscountResult.class)).thenThrow(new RestClientException("Service unavailable"));

        // When
        DiscountResult result = promotionServiceClient.validatePromotion(request);

        // Then
        assertNotNull(result);
        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("unavailable"));
        assertEquals(BigDecimal.ZERO, result.getDiscountAmount());
        assertEquals(request.getPurchaseAmount(), result.getFinalAmount());
    }

    @Test
    @DisplayName("Should apply promotion and increment usage")
    void shouldApplyPromotionAndIncrementUsage() {
        // Given
        String promotionCode = "SAVE20";

        DiscountResult expectedResult = DiscountResult.builder()
            .valid(true)
            .message("Promotion applied successfully")
            .discountAmount(BigDecimal.valueOf(20.00))
            .finalAmount(BigDecimal.valueOf(80.00))
            .promotionCode("SAVE20")
            .build();

        stubApplyPost();
        when(responseSpec.body(DiscountResult.class)).thenReturn(expectedResult);

        // When
        DiscountResult result = promotionServiceClient.applyPromotion(
            promotionCode, BigDecimal.valueOf(100.00));

        // Then
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(promotionCode, result.getPromotionCode());
    }

    /**
     * Regression: /apply re-validates before redeeming, so it requires the same
     * JSON body as /validate. Posting a bare code string was rejected by the
     * endpoint and the usage counter was never incremented.
     */
    @Test
    @DisplayName("should_postValidationRequestBody_when_applyingPromotion")
    void should_postValidationRequestBody_when_applyingPromotion() {
        stubApplyPost();
        when(responseSpec.body(DiscountResult.class))
            .thenReturn(DiscountResult.builder().valid(true).build());

        promotionServiceClient.applyPromotion("SAVE20", BigDecimal.valueOf(100.00));

        ArgumentCaptor<PromotionValidationRequest> body =
            ArgumentCaptor.forClass(PromotionValidationRequest.class);
        verify(requestBodySpec).body(body.capture());
        assertEquals("SAVE20", body.getValue().getCode());
        assertEquals(BigDecimal.valueOf(100.00), body.getValue().getPurchaseAmount());
    }

    @Test
    @DisplayName("should_targetApplyEndpoint_when_applyingPromotion")
    void should_targetApplyEndpoint_when_applyingPromotion() {
        stubApplyPost();
        when(responseSpec.body(DiscountResult.class))
            .thenReturn(DiscountResult.builder().valid(true).build());

        promotionServiceClient.applyPromotion("SAVE20", BigDecimal.valueOf(100.00));

        verify(requestBodyUriSpec).uri("/api/promotions/apply");
    }

    private void stubApplyPost() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(PromotionValidationRequest.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    @DisplayName("should_returnTierDiscountPercent_when_loyaltyLookupSucceeds")
    void should_returnTierDiscountPercent_when_loyaltyLookupSucceeds() {
        stubLoyaltyGet();
        when(responseSpec.body(LoyaltyResult.class)).thenReturn(
            LoyaltyResult.builder().tier("GOLD").discountPercent(BigDecimal.valueOf(10)).build());

        BigDecimal percent = promotionServiceClient.getLoyaltyDiscountPercent("user-1");

        assertEquals(0, BigDecimal.valueOf(10).compareTo(percent));
    }

    @Test
    @DisplayName("should_returnZero_when_loyaltyResponseHasNullPercent")
    void should_returnZero_when_loyaltyResponseHasNullPercent() {
        stubLoyaltyGet();
        when(responseSpec.body(LoyaltyResult.class)).thenReturn(
            LoyaltyResult.builder().tier("BRONZE").discountPercent(null).build());

        BigDecimal percent = promotionServiceClient.getLoyaltyDiscountPercent("user-1");

        assertEquals(0, BigDecimal.ZERO.compareTo(percent));
    }

    @Test
    @DisplayName("should_returnZero_when_loyaltyResponseBodyNull")
    void should_returnZero_when_loyaltyResponseBodyNull() {
        stubLoyaltyGet();
        when(responseSpec.body(LoyaltyResult.class)).thenReturn(null);

        BigDecimal percent = promotionServiceClient.getLoyaltyDiscountPercent("user-1");

        assertEquals(0, BigDecimal.ZERO.compareTo(percent));
    }

    @Test
    @DisplayName("should_returnZero_when_userIdBlank")
    void should_returnZero_when_userIdBlank() {
        BigDecimal percent = promotionServiceClient.getLoyaltyDiscountPercent("  ");

        assertEquals(0, BigDecimal.ZERO.compareTo(percent));
        verify(restClient, never()).get();
    }

    @Test
    @DisplayName("should_returnZero_when_loyaltyServiceUnavailable")
    void should_returnZero_when_loyaltyServiceUnavailable() {
        stubLoyaltyGet();
        when(responseSpec.body(LoyaltyResult.class))
            .thenThrow(new RestClientException("Service unavailable"));

        BigDecimal percent = promotionServiceClient.getLoyaltyDiscountPercent("user-1");

        assertEquals(0, BigDecimal.ZERO.compareTo(percent));
    }

    private void stubLoyaltyGet() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }
}
