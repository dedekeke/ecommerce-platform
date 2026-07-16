package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.savedmethod.SavedPaymentMethodController;
import com.ecommerce.paymentservice.savedmethod.SavedPaymentMethodNotFoundException;
import com.ecommerce.paymentservice.savedmethod.SavedPaymentMethodService;
import com.ecommerce.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves — through the REAL Spring MVC dispatcher + {@link PaymentApiExceptionHandler} — that
 * savedmethod and confirm-saved errors map to the correct HTTP status. Before the advice was
 * widened past the {@code api} package these fell through to a generic 500. Uses standalone
 * MockMvc so no heavy Spring context (Eureka/Kafka/gRPC) is booted; auth is supplied via the
 * {@code X-User-Id} fallback header since no JWT argument resolver is wired here.
 */
@DisplayName("Payment API exception -> HTTP status mapping (real dispatcher)")
class PaymentExceptionMappingMvcTest {

    private SavedPaymentMethodService savedMethodService;
    private PaymentService paymentService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        savedMethodService = Mockito.mock(SavedPaymentMethodService.class);
        paymentService = Mockito.mock(PaymentService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new SavedPaymentMethodController(savedMethodService),
                        new PaymentIntentController(paymentService, savedMethodService))
                .setControllerAdvice(new PaymentApiExceptionHandler())
                // Resolve @AuthenticationPrincipal Jwt to null so the X-User-Id fallback is used.
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("cross-user list -> 403 (not 500)")
    void crossUserList_should_return403() throws Exception {
        mockMvc.perform(get("/api/payments/methods/user/other-user")
                        .header("X-User-Id", "user-1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("delete a missing method -> 404 (not 500)")
    void deleteMissing_should_return404() throws Exception {
        doThrow(new SavedPaymentMethodNotFoundException("Saved payment method not found: 9"))
                .when(savedMethodService).delete(eq("user-1"), eq(9L));

        mockMvc.perform(delete("/api/payments/methods/9").header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("confirm a not-succeeded SetupIntent -> 409")
    void confirmNotSucceeded_should_return409() throws Exception {
        when(savedMethodService.confirmSetupIntent(eq("user-1"), anyString()))
                .thenThrow(new IllegalStateException("SetupIntent seti_1 is not completed: requires_payment_method"));

        mockMvc.perform(post("/api/payments/methods/confirm")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setupIntentId\":\"seti_1\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("pay with a method the caller does not own -> 403 (no charge)")
    void confirmSavedCrossUser_should_return403() throws Exception {
        when(savedMethodService.payWithSavedMethod(eq("attacker"), anyString(), anyString()))
                .thenThrow(new SecurityException("Saved payment method does not belong to user attacker"));

        mockMvc.perform(post("/api/payments/intents/confirm-saved")
                        .header("X-User-Id", "attacker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentIntentId\":\"pi_1\",\"paymentMethodId\":\"pm_victim\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("confirm-saved with a blank payment method -> 400 validation")
    void confirmSavedBlank_should_return400() throws Exception {
        mockMvc.perform(post("/api/payments/intents/confirm-saved")
                        .header("X-User-Id", "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentIntentId\":\"pi_1\",\"paymentMethodId\":\"\"}"))
                .andExpect(status().isBadRequest());

        Mockito.verify(savedMethodService, Mockito.never())
                .payWithSavedMethod(anyString(), anyString(), anyString());
        Mockito.verifyNoInteractions(paymentService);
    }
}
