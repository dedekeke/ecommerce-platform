package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.client.dto.DiscountResult;
import com.ecommerce.orderservice.client.dto.PromotionValidationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private PromotionServiceClient promotionServiceClient;

    private static final String PROMOTION_SERVICE_URL = "http://localhost:8090";

    @BeforeEach
    void setUp() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(restClient);

        promotionServiceClient = new PromotionServiceClient(restClientBuilder, PROMOTION_SERVICE_URL);
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

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(String.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(DiscountResult.class)).thenReturn(expectedResult);

        // When
        DiscountResult result = promotionServiceClient.applyPromotion(promotionCode);

        // Then
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(promotionCode, result.getPromotionCode());
    }
}
