package com.ecommerce.paymentservice.api;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-context security test for the PaymentIntent endpoint. Boots the real Spring Security filter
 * chain ({@code security.enabled=true}) so an unauthenticated request is rejected with 401 — unlike
 * the standalone controller test which bypasses security. Also asserts the owning user is derived
 * from the JWT subject and a body that claims a different user is rejected with 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "grpc.server.port=-1",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/"
})
@DisplayName("PaymentIntentController Security Tests")
class PaymentIntentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    // Stub the decoder so the resource server starts without contacting Auth0; the authenticated
    // principal is injected via the jwt() request post-processor instead of a real bearer token.
    @MockBean
    private JwtDecoder jwtDecoder;

    private static final String BODY = """
            {"orderId":"order-1","userId":"auth0|user-1","amount":42.00,"currency":"USD"}""";

    private Payment payment() {
        return Payment.builder()
                .id(7L).orderId("order-1").userId("auth0|user-1")
                .amount(new BigDecimal("42.00")).currency("USD")
                .status(PaymentStatus.PENDING)
                .paymentIntentId("pi_123").clientSecret("pi_123_secret_abc")
                .build();
    }

    @Test
    @DisplayName("should return 401 when POST /api/payments/intents is unauthenticated")
    void should_return401_when_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/payments/intents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verify(paymentService, never()).createPaymentIntent(any(), any(), any(), any());
    }

    @Test
    @DisplayName("should derive userId from the JWT subject when authenticated")
    void should_useJwtSubject_when_authenticated() throws Exception {
        when(paymentService.createPaymentIntent(eq("order-1"), eq("auth0|user-1"), any(), eq("USD")))
                .thenReturn(payment());

        mockMvc.perform(post("/api/payments/intents")
                        .with(jwt().jwt(j -> j.subject("auth0|user-1")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientSecret").value("pi_123_secret_abc"));

        verify(paymentService).createPaymentIntent(eq("order-1"), eq("auth0|user-1"), any(), eq("USD"));
    }

    @Test
    @DisplayName("should return 403 when the body userId does not match the JWT subject")
    void should_return403_when_bodyUserMismatchesToken() throws Exception {
        mockMvc.perform(post("/api/payments/intents")
                        .with(jwt().jwt(j -> j.subject("auth0|someone-else")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());

        verify(paymentService, never()).createPaymentIntent(any(), any(), any(), any());
    }
}
