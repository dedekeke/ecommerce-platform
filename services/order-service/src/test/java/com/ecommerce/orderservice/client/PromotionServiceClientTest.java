package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.client.dto.DiscountResult;
import com.ecommerce.orderservice.client.dto.LoyaltyResult;
import com.ecommerce.orderservice.client.dto.PromotionValidationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

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
    private static final boolean SECURITY_ENABLED = true;
    private static final boolean SECURITY_DISABLED = false;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.defaultHeader(anyString(), any(String[].class)))
            .thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);
        meterRegistry = new SimpleMeterRegistry();

        promotionServiceClient = newClient(INTERNAL_TOKEN, SECURITY_ENABLED);
    }

    private PromotionServiceClient newClient(String token, boolean securityEnabled) {
        return new PromotionServiceClient(
            restClientBuilder, PROMOTION_SERVICE_URL, token, securityEnabled, meterRegistry);
    }

    private double applyAuthFailureCount() {
        return meterRegistry.counter(PromotionServiceClient.APPLY_AUTH_FAILURE_METRIC).count();
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
    @DisplayName("should_omitInternalServiceToken_when_tokenBlankAndSecurityDisabled")
    void should_omitInternalServiceToken_when_tokenBlankAndSecurityDisabled() {
        clearInvocations(restClientBuilder);

        newClient("  ", SECURITY_DISABLED);

        verify(restClientBuilder, never()).defaultHeader(
            eq(PromotionServiceClient.INTERNAL_TOKEN_HEADER), any(String[].class));
    }

    /**
     * Fail CLOSED at boot instead of fail-open at runtime: with security on and
     * no credential, every /apply is 401'd, the saga swallows it, and
     * limited-use promo codes become unlimited with no visible symptom.
     */
    @ParameterizedTest(name = "token=\"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("should_failFast_when_tokenBlankAndSecurityEnabled")
    void should_failFast_when_tokenBlankAndSecurityEnabled(String token) {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> newClient(token, SECURITY_ENABLED));

        assertTrue(thrown.getMessage().contains("INTERNAL_SERVICE_TOKEN"));
    }

    // ==================== /apply authorization failures ====================

    @ParameterizedTest(name = "status={0}")
    @ValueSource(ints = {401, 403})
    @DisplayName("should_countApplyAuthFailure_when_promotionServiceRejectsCredential")
    void should_countApplyAuthFailure_when_promotionServiceRejectsCredential(int status) {
        stubApplyPost();
        when(responseSpec.body(DiscountResult.class)).thenThrow(httpError(status));

        promotionServiceClient.applyPromotion("SAVE20", BigDecimal.valueOf(100.00));

        assertEquals(1.0, applyAuthFailureCount());
    }

    /**
     * Availability over strictness: the order is already being created, so an
     * apply rejection degrades to "not applied" rather than failing checkout.
     * It must be observable (counter + ERROR marker), never silent.
     */
    @Test
    @DisplayName("should_returnInvalidResult_when_applyRejectedWithUnauthorized")
    void should_returnInvalidResult_when_applyRejectedWithUnauthorized() {
        stubApplyPost();
        when(responseSpec.body(DiscountResult.class)).thenThrow(httpError(401));

        DiscountResult result = promotionServiceClient.applyPromotion("SAVE20", BigDecimal.valueOf(100.00));

        assertFalse(result.isValid());
    }

    /**
     * A permanent 401/403 must NOT propagate: Resilience4j would retry it three
     * times per order and open the breaker for the healthy validate/loyalty
     * calls that share it.
     */
    @Test
    @DisplayName("should_notPropagateException_when_applyRejectedWithForbidden")
    void should_notPropagateException_when_applyRejectedWithForbidden() {
        stubApplyPost();
        when(responseSpec.body(DiscountResult.class)).thenThrow(httpError(403));

        assertDoesNotThrow(() -> promotionServiceClient.applyPromotion("SAVE20", BigDecimal.valueOf(100.00)));
    }

    /**
     * The counter must mean "credential is broken", not "promotion-service had a
     * bad day" — a transient failure keeps the existing circuit-breaker path.
     */
    @Test
    @DisplayName("should_notCountAuthFailure_when_applyFailsTransiently")
    void should_notCountAuthFailure_when_applyFailsTransiently() {
        stubApplyPost();
        when(responseSpec.body(DiscountResult.class))
            .thenThrow(new RestClientException("connection reset"));

        assertThrows(RestClientException.class,
            () -> promotionServiceClient.applyPromotion("SAVE20", BigDecimal.valueOf(100.00)));
        assertEquals(0.0, applyAuthFailureCount());
    }

    @Test
    @DisplayName("should_notCountAuthFailure_when_applySucceeds")
    void should_notCountAuthFailure_when_applySucceeds() {
        stubApplyPost();
        when(responseSpec.body(DiscountResult.class))
            .thenReturn(DiscountResult.builder().valid(true).build());

        promotionServiceClient.applyPromotion("SAVE20", BigDecimal.valueOf(100.00));

        assertEquals(0.0, applyAuthFailureCount());
    }

    private static HttpClientErrorException httpError(int status) {
        return HttpClientErrorException.create(
            HttpStatusCode.valueOf(status), "rejected", HttpHeaders.EMPTY, new byte[0], null);
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
