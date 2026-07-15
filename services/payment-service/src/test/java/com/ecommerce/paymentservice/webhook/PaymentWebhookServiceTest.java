package com.ecommerce.paymentservice.webhook;

import com.stripe.model.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the webhook orchestrator: verify-then-process ordering, 400 on
 * signature failure with no reconciliation, and idempotent swallowing of a
 * duplicate delivery.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentWebhookService Unit Tests")
class PaymentWebhookServiceTest {

    @Mock
    private StripeWebhookVerifier verifier;

    @Mock
    private StripeWebhookProcessor processor;

    @InjectMocks
    private PaymentWebhookService service;

    private static final String PAYLOAD = "{\"id\":\"evt_1\"}";
    private static final String SIG = "t=1,v1=abc";

    @Test
    @DisplayName("should process the event when the signature verifies")
    void should_process_when_signatureValid() {
        Event event = StripeWebhookTestSupport.parse(
                StripeWebhookTestSupport.succeededEventJson("evt_1", "pi_1", "ch_1"));
        when(verifier.verifyAndParse(PAYLOAD, SIG)).thenReturn(event);

        service.handle(PAYLOAD, SIG);

        verify(processor).process(event);
    }

    @Test
    @DisplayName("should not process and should propagate when signature verification fails")
    void should_notProcess_when_signatureInvalid() {
        when(verifier.verifyAndParse(PAYLOAD, SIG))
                .thenThrow(new WebhookVerificationException("Invalid Stripe webhook signature"));

        assertThatThrownBy(() -> service.handle(PAYLOAD, SIG))
                .isInstanceOf(WebhookVerificationException.class);

        verify(processor, never()).process(any());
    }

    @Test
    @DisplayName("should swallow a duplicate delivery as an idempotent success")
    void should_swallowDuplicate_when_eventAlreadyProcessed() {
        Event event = StripeWebhookTestSupport.parse(
                StripeWebhookTestSupport.succeededEventJson("evt_1", "pi_1", "ch_1"));
        when(verifier.verifyAndParse(PAYLOAD, SIG)).thenReturn(event);
        doThrow(new DataIntegrityViolationException("duplicate key")).when(processor).process(event);

        assertThatCode(() -> service.handle(PAYLOAD, SIG)).doesNotThrowAnyException();
    }
}
