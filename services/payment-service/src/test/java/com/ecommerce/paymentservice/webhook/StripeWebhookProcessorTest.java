package com.ecommerce.paymentservice.webhook;

import com.ecommerce.paymentservice.service.PaymentService;
import com.stripe.model.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the webhook reconciliation worker: correct dispatch per event
 * type, insert-first dedup ordering, and safe handling of unhandled events.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StripeWebhookProcessor Unit Tests")
class StripeWebhookProcessorTest {

    @Mock
    private ProcessedStripeEventRepository processedEventRepository;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private StripeWebhookProcessor processor;

    @Test
    @DisplayName("should record the event and mark the payment succeeded on payment_intent.succeeded")
    void should_markSucceeded_when_intentSucceeded() {
        Event event = StripeWebhookTestSupport.parse(
                StripeWebhookTestSupport.succeededEventJson("evt_1", "pi_1", "ch_1"));

        processor.process(event);

        verify(processedEventRepository).saveAndFlush(any(ProcessedStripeEvent.class));
        verify(paymentService).markPaymentSucceeded("pi_1", "ch_1");
        verify(paymentService, never()).markPaymentFailed(any(), any());
    }

    @Test
    @DisplayName("should mark the payment failed with the Stripe error message on payment_intent.payment_failed")
    void should_markFailed_when_intentFailed() {
        Event event = StripeWebhookTestSupport.parse(
                StripeWebhookTestSupport.failedEventJson("evt_2", "pi_2", "Your card was declined."));

        processor.process(event);

        verify(paymentService).markPaymentFailed("pi_2", "Your card was declined.");
        verify(paymentService, never()).markPaymentSucceeded(any(), any());
    }

    @Test
    @DisplayName("should mark the payment failed on payment_intent.canceled")
    void should_markFailed_when_intentCanceled() {
        Event event = StripeWebhookTestSupport.parse(
                StripeWebhookTestSupport.canceledEventJson("evt_3", "pi_3"));

        processor.process(event);

        verify(paymentService).markPaymentFailed(eq("pi_3"), any());
    }

    @Test
    @DisplayName("should ignore an unhandled event type without recording or reconciling")
    void should_ignore_when_eventTypeUnhandled() {
        Event event = StripeWebhookTestSupport.parse(
                StripeWebhookTestSupport.unhandledEventJson("evt_4"));

        processor.process(event);

        verifyNoInteractions(processedEventRepository);
        verifyNoInteractions(paymentService);
    }

    @Test
    @DisplayName("should propagate a duplicate-key violation before touching payment state")
    void should_propagateDuplicate_when_eventAlreadyRecorded() {
        Event event = StripeWebhookTestSupport.parse(
                StripeWebhookTestSupport.succeededEventJson("evt_1", "pi_1", "ch_1"));
        when(processedEventRepository.saveAndFlush(any(ProcessedStripeEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key: evt_1"));

        assertThatThrownBy(() -> processor.process(event))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(paymentService, never()).markPaymentSucceeded(any(), any());
    }
}
