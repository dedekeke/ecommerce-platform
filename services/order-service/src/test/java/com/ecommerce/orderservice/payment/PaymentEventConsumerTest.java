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
    void should_dropPoisonMessageWithoutThrowing_when_payloadIsNotJson() {
        assertThatCode(() -> consumer.handlePaymentCompleted("not-json", "evt-4"))
            .doesNotThrowAnyException();

        verifyNoInteractions(handler);
    }

    @Test
    void should_dropMessageWithoutDelegating_when_orderIdMissing() {
        consumer.handlePaymentCompleted("{\"eventType\":\"PAYMENT_COMPLETED\"}", "evt-5");

        verifyNoInteractions(handler);
    }
}
