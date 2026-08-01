package com.ecommerce.paymentservice.kafka;

import com.ecommerce.paymentservice.outbox.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Verifies the publisher records outbox events with the correct topic /
 * event-type routing and propagates outbox failures so the surrounding
 * @Transactional method rolls back.
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventPublisherTest {

    @Mock
    private OutboxService outboxService;

    private PaymentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PaymentEventPublisher(outboxService);
    }

    @Test
    void should_routeCompletedEventToCorrectTopic_when_publishingPaymentCompleted() {
        PaymentEvent event = buildEvent("PAYMENT_COMPLETED");

        publisher.publishPaymentCompletedEvent(event);

        ArgumentCaptor<PaymentEvent> captor = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(outboxService).recordEvent(
            eq("Payment"),
            eq("order-99"),
            eq("PAYMENT_COMPLETED"),
            eq("payment.completed"),
            captor.capture()
        );
        assertThat(captor.getValue().getEventType()).isEqualTo("PAYMENT_COMPLETED");
        assertThat(captor.getValue().getOrderId()).isEqualTo("order-99");
    }

    @Test
    void should_routeFailedEventToCorrectTopic_when_publishingPaymentFailed() {
        PaymentEvent event = buildEvent("PAYMENT_FAILED");

        publisher.publishPaymentFailedEvent(event);

        verify(outboxService).recordEvent(
            eq("Payment"),
            eq("order-99"),
            eq("PAYMENT_FAILED"),
            eq("payment.failed"),
            any(PaymentEvent.class)
        );
    }

    @Test
    void should_propagateException_when_outboxRecordFails() {
        doThrow(new RuntimeException("DB down"))
            .when(outboxService).recordEvent(any(), any(), any(), any(), any());

        PaymentEvent event = buildEvent("PAYMENT_COMPLETED");

        assertThatThrownBy(() -> publisher.publishPaymentCompletedEvent(event))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("DB down");
    }

    private PaymentEvent buildEvent(String type) {
        return PaymentEvent.builder()
            .eventType(type)
            .paymentId(1L)
            .orderId("order-99")
            .userId("user-1")
            .paymentIntentId("pi_123")
            .transactionId("txn_abc")
            .amount(new BigDecimal("42.00"))
            .currency("USD")
            .status("COMPLETED")
            .timestamp(LocalDateTime.now())
            .build();
    }
}
