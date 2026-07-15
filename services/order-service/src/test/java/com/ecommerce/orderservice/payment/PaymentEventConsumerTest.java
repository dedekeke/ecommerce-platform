package com.ecommerce.orderservice.payment;

import com.ecommerce.orderservice.client.ResilientGrpcClient;
import com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationRequest;
import com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the thin Kafka adapter: JSON/header parsing, delegation to the
 * transactional handler, and reservation release on the failed path.
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    @Mock
    private PaymentEventHandler handler;

    @Mock
    private ResilientGrpcClient grpcClient;

    @Captor
    private ArgumentCaptor<PaymentEventEnvelope> envelopeCaptor;

    @Captor
    private ArgumentCaptor<ReleaseReservationRequest> releaseCaptor;

    private PaymentEventConsumer consumer;

    private static final String COMPLETED_JSON = """
        {"eventType":"PAYMENT_COMPLETED","paymentId":42,"orderId":"order-1",
         "userId":"user-1","paymentIntentId":"pi_1","transactionId":"txn_1",
         "amount":19.99,"currency":"USD","status":"COMPLETED","timestamp":"2026-07-16T10:00:00"}
        """;

    private static final String FAILED_JSON = """
        {"eventType":"PAYMENT_FAILED","orderId":"order-1","paymentIntentId":"pi_1",
         "status":"FAILED","failureReason":"card_declined","amount":19.99,"currency":"USD"}
        """;

    @BeforeEach
    void setUp() {
        consumer = new PaymentEventConsumer(handler, grpcClient, new ObjectMapper());
    }

    @Test
    void should_delegateWithParsedEnvelopeAndEventId_when_paymentCompleted() {
        consumer.handlePaymentCompleted(COMPLETED_JSON, "evt-1");

        verify(handler).onPaymentCompleted(eq("evt-1"), envelopeCaptor.capture());
        assertThat(envelopeCaptor.getValue().orderId()).isEqualTo("order-1");
    }

    @Test
    void should_releaseReservationByOrderId_when_failedHandlerSignalsRelease() {
        when(handler.onPaymentFailed(eq("evt-2"), any())).thenReturn(true);
        when(grpcClient.releaseReservation(any()))
            .thenReturn(ReleaseReservationResponse.newBuilder().setSuccess(true).build());

        consumer.handlePaymentFailed(FAILED_JSON, "evt-2");

        verify(grpcClient).releaseReservation(releaseCaptor.capture());
        assertThat(releaseCaptor.getValue().getOrderId()).isEqualTo("order-1");
    }

    @Test
    void should_notReleaseReservation_when_failedHandlerReportsNoop() {
        when(handler.onPaymentFailed(eq("evt-3"), any())).thenReturn(false);

        consumer.handlePaymentFailed(FAILED_JSON, "evt-3");

        verify(grpcClient, never()).releaseReservation(any());
    }

    @Test
    void should_throwForDeadLetter_when_payloadIsNotJson() {
        // Non-retryable: the error handler dead-letters it instead of dropping.
        assertThatThrownBy(() -> consumer.handlePaymentCompleted("not-json", "evt-4"))
            .isInstanceOf(PaymentEventProcessingException.class);

        verifyNoInteractions(handler);
    }

    @Test
    void should_throwForDeadLetter_when_orderIdMissing() {
        assertThatThrownBy(() ->
            consumer.handlePaymentCompleted("{\"eventType\":\"PAYMENT_COMPLETED\"}", "evt-5"))
            .isInstanceOf(PaymentEventProcessingException.class);

        verifyNoInteractions(handler);
    }

    @Test
    void should_ackWithoutThrowing_when_handlerDedupesOrNoops() {
        // A dedup skip / no-op returns normally from the handler — the consumer
        // must NOT throw, so the record is acked and never dead-lettered.
        assertThatCode(() -> consumer.handlePaymentCompleted(COMPLETED_JSON, "evt-6"))
            .doesNotThrowAnyException();

        verify(handler).onPaymentCompleted(eq("evt-6"), any());
    }
}
