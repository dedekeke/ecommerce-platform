package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.gateway.PaymentGatewayResponse;
import com.ecommerce.paymentservice.gateway.PaymentIntentProvider;
import com.ecommerce.paymentservice.kafka.PaymentEventPublisher;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentIntentProvider paymentProvider;

    @Mock
    private PaymentEventPublisher eventPublisher;

    @InjectMocks
    private PaymentService paymentService;

    private static final String ORDER_ID = "order-1";
    private static final String USER_ID = "auth0|user-1";
    private static final String INTENT_ID = "pi_123";

    private Payment savedPayment(PaymentStatus status) {
        return Payment.builder()
                .id(1L)
                .orderId(ORDER_ID)
                .userId(USER_ID)
                .amount(new BigDecimal("42.00"))
                .currency("USD")
                .status(status)
                .paymentIntentId(INTENT_ID)
                .clientSecret("pi_123_secret_abc")
                .build();
    }

    @Nested
    @DisplayName("createPaymentIntent")
    class CreatePaymentIntent {

        @Test
        @DisplayName("should persist a pending payment carrying the provider client secret")
        void should_persistPendingPayment_when_createIntent() {
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
            when(paymentProvider.createPaymentIntent(ORDER_ID, USER_ID, new BigDecimal("42.00"), "USD"))
                    .thenReturn(PaymentGatewayResponse.builder()
                            .success(true).paymentIntentId(INTENT_ID).clientSecret("pi_123_secret_abc")
                            .status("PENDING").build());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.createPaymentIntent(ORDER_ID, USER_ID, new BigDecimal("42.00"), "USD");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(result.getPaymentIntentId()).isEqualTo(INTENT_ID);
            assertThat(result.getClientSecret()).isEqualTo("pi_123_secret_abc");
        }

        @Test
        @DisplayName("should reuse the existing pending payment instead of creating a second Stripe intent")
        void should_reuseExistingPending_when_orderAlreadyHasPendingIntent() {
            Payment existing = savedPayment(PaymentStatus.PENDING);
            when(paymentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(existing));

            Payment result = paymentService.createPaymentIntent(ORDER_ID, USER_ID, new BigDecimal("42.00"), "USD");

            assertThat(result).isSameAs(existing);
            verify(paymentProvider, never()).createPaymentIntent(any(), any(), any(), any());
            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("should create a new intent when the existing payment is not pending")
        void should_createNewIntent_when_existingPaymentNotPending() {
            when(paymentRepository.findByOrderId(ORDER_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.FAILED)));
            when(paymentProvider.createPaymentIntent(ORDER_ID, USER_ID, new BigDecimal("42.00"), "USD"))
                    .thenReturn(PaymentGatewayResponse.builder()
                            .success(true).paymentIntentId(INTENT_ID).clientSecret("pi_123_secret_abc")
                            .status("PENDING").build());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            Payment result = paymentService.createPaymentIntent(ORDER_ID, USER_ID, new BigDecimal("42.00"), "USD");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentProvider).createPaymentIntent(ORDER_ID, USER_ID, new BigDecimal("42.00"), "USD");
        }
    }

    @Nested
    @DisplayName("confirmPayment")
    class ConfirmPayment {

        @Test
        @DisplayName("should complete payment and publish completed event when gateway succeeds")
        void should_completeAndPublish_when_gatewaySucceeds() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.PENDING)));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentProvider.confirmPayment(INTENT_ID, "pm_card_visa"))
                    .thenReturn(PaymentGatewayResponse.builder()
                            .success(true).paymentIntentId(INTENT_ID).transactionId("ch_456").status("COMPLETED").build());

            Payment result = paymentService.confirmPayment(INTENT_ID, "pm_card_visa");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(result.getTransactionId()).isEqualTo("ch_456");
            verify(eventPublisher).publishPaymentCompletedEvent(any());
            verify(eventPublisher, never()).publishPaymentFailedEvent(any());
        }

        @Test
        @DisplayName("should fail payment and publish failed event when gateway declines")
        void should_failAndPublish_when_gatewayDeclines() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.PENDING)));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentProvider.confirmPayment(INTENT_ID, "pm_card_visa"))
                    .thenReturn(PaymentGatewayResponse.builder()
                            .success(false).paymentIntentId(INTENT_ID).status("FAILED")
                            .errorMessage("Payment declined by bank").build());

            Payment result = paymentService.confirmPayment(INTENT_ID, "pm_card_visa");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(result.getFailureReason()).isEqualTo("Payment declined by bank");
            verify(eventPublisher).publishPaymentFailedEvent(any());
            verify(eventPublisher, never()).publishPaymentCompletedEvent(any());
        }

        @Test
        @DisplayName("should throw when payment intent is not found")
        void should_throw_when_intentNotFound() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.confirmPayment(INTENT_ID, "pm_card_visa"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(INTENT_ID);

            verify(paymentProvider, never()).confirmPayment(any(), any());
        }
    }

    @Nested
    @DisplayName("webhook reconciliation")
    class WebhookReconciliation {

        @Test
        @DisplayName("should complete a pending payment and publish completed on succeeded webhook")
        void should_completeAndPublish_when_succeededWebhook() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.PENDING)));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            paymentService.markPaymentSucceeded(INTENT_ID, "ch_web");

            verify(eventPublisher).publishPaymentCompletedEvent(any());
            verify(eventPublisher, never()).publishPaymentFailedEvent(any());
        }

        @Test
        @DisplayName("should converge idempotently and NOT re-emit when already completed by client confirm")
        void should_notReemit_when_alreadyCompleted() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.COMPLETED)));

            paymentService.markPaymentSucceeded(INTENT_ID, "ch_web");

            verify(paymentRepository, never()).save(any());
            verify(eventPublisher, never()).publishPaymentCompletedEvent(any());
        }

        @Test
        @DisplayName("should acknowledge without action or throwing when the intent is unknown")
        void should_ack_when_succeededIntentUnknown() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID)).thenReturn(Optional.empty());

            paymentService.markPaymentSucceeded(INTENT_ID, "ch_web");

            verify(paymentRepository, never()).save(any());
            verify(eventPublisher, never()).publishPaymentCompletedEvent(any());
        }

        @Test
        @DisplayName("should fail a pending payment and publish failed on failed webhook")
        void should_failAndPublish_when_failedWebhook() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.PENDING)));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

            paymentService.markPaymentFailed(INTENT_ID, "Your card was declined.");

            verify(eventPublisher).publishPaymentFailedEvent(any());
            verify(eventPublisher, never()).publishPaymentCompletedEvent(any());
        }

        @Test
        @DisplayName("should NOT downgrade a completed payment when a late failure webhook arrives")
        void should_notDowngrade_when_completedThenFailedWebhook() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.COMPLETED)));

            paymentService.markPaymentFailed(INTENT_ID, "late failure");

            verify(paymentRepository, never()).save(any());
            verify(eventPublisher, never()).publishPaymentFailedEvent(any());
        }

        @Test
        @DisplayName("should be a no-op when a failure webhook repeats for an already failed payment")
        void should_noop_when_alreadyFailed() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.FAILED)));

            paymentService.markPaymentFailed(INTENT_ID, "again");

            verify(paymentRepository, never()).save(any());
            verify(eventPublisher, never()).publishPaymentFailedEvent(any());
        }
    }

    @Nested
    @DisplayName("refundPayment")
    class RefundPayment {

        @Test
        @DisplayName("should refund a completed payment")
        void should_refund_when_paymentCompleted() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.COMPLETED)));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(paymentProvider.refundPayment(eq(INTENT_ID), any(), any()))
                    .thenReturn(PaymentGatewayResponse.builder()
                            .success(true).paymentIntentId(INTENT_ID).transactionId("re_1").status("REFUNDED").build());

            Payment result = paymentService.refundPayment(INTENT_ID, new BigDecimal("10.00"), "requested_by_customer");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(result.getTransactionId()).isEqualTo("re_1");
        }

        @Test
        @DisplayName("should reject refund when payment is not completed")
        void should_reject_when_paymentNotCompleted() {
            when(paymentRepository.findByPaymentIntentId(INTENT_ID))
                    .thenReturn(Optional.of(savedPayment(PaymentStatus.PENDING)));

            assertThatThrownBy(() -> paymentService.refundPayment(INTENT_ID, new BigDecimal("10.00"), "reason"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("cannot be refunded");

            verify(paymentProvider, never()).refundPayment(any(), any(), any());
        }
    }
}
