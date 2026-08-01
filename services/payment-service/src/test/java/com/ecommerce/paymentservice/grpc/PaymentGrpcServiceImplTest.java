package com.ecommerce.paymentservice.grpc;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.grpc.proto.ConfirmPaymentRequest;
import com.ecommerce.paymentservice.grpc.proto.ConfirmPaymentResponse;
import com.ecommerce.paymentservice.grpc.proto.CreatePaymentIntentRequest;
import com.ecommerce.paymentservice.grpc.proto.CreatePaymentIntentResponse;
import com.ecommerce.paymentservice.grpc.proto.RefundPaymentRequest;
import com.ecommerce.paymentservice.grpc.proto.RefundPaymentResponse;
import com.ecommerce.paymentservice.service.PaymentService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentGrpcServiceImpl Tests")
class PaymentGrpcServiceImplTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private StreamObserver<ConfirmPaymentResponse> responseObserver;

    @Mock
    private StreamObserver<CreatePaymentIntentResponse> createResponseObserver;

    @Mock
    private StreamObserver<RefundPaymentResponse> refundResponseObserver;

    @InjectMocks
    private PaymentGrpcServiceImpl service;

    private static final String INTENT_ID = "pi_123";
    private static final String RAW_STRIPE_DETAIL =
            "Your card was declined. [code: card_declined, decline_code: insufficient_funds, "
                    + "request-id: req_abc123, pi: pi_secret_internal]";

    @Test
    @DisplayName("should not leak raw failureReason into the gRPC confirm response message on failure")
    void should_notLeakFailureReason_when_confirmFails() {
        Payment failed = Payment.builder()
                .id(1L)
                .paymentIntentId(INTENT_ID)
                .status(PaymentStatus.FAILED)
                .failureReason(RAW_STRIPE_DETAIL)
                .build();
        when(paymentService.confirmPayment(anyString(), any())).thenReturn(failed);

        service.confirmPayment(
                ConfirmPaymentRequest.newBuilder().setPaymentIntentId(INTENT_ID).build(),
                responseObserver);

        ConfirmPaymentResponse response = captureResponse();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).doesNotContain(RAW_STRIPE_DETAIL);
        assertThat(response.getMessage()).doesNotContain("decline_code");
        assertThat(response.getMessage()).doesNotContain("req_abc123");
        assertThat(response.getStatus()).isEqualTo(com.ecommerce.paymentservice.grpc.proto.PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("should return transactionId and success message when confirm succeeds")
    void should_returnSuccess_when_confirmSucceeds() {
        Payment completed = Payment.builder()
                .id(1L)
                .paymentIntentId(INTENT_ID)
                .status(PaymentStatus.COMPLETED)
                .transactionId("ch_999")
                .build();
        when(paymentService.confirmPayment(anyString(), any())).thenReturn(completed);

        service.confirmPayment(
                ConfirmPaymentRequest.newBuilder().setPaymentIntentId(INTENT_ID).build(),
                responseObserver);

        ConfirmPaymentResponse response = captureResponse();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTransactionId()).isEqualTo("ch_999");
        assertThat(response.getStatus()).isEqualTo(com.ecommerce.paymentservice.grpc.proto.PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("should not leak raw exception detail into the gRPC createPaymentIntent response on exception")
    void should_notLeakExceptionDetail_when_createPaymentIntentThrows() {
        when(paymentService.createPaymentIntent(anyString(), anyString(), any(), anyString()))
                .thenThrow(new RuntimeException(RAW_STRIPE_DETAIL));

        service.createPaymentIntent(
                CreatePaymentIntentRequest.newBuilder()
                        .setOrderId("order-1")
                        .setUserId("user-1")
                        .setAmount(10.0)
                        .setCurrency("USD")
                        .build(),
                createResponseObserver);

        CreatePaymentIntentResponse response = captureCreateResponse();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).doesNotContain(RAW_STRIPE_DETAIL);
        assertThat(response.getMessage()).doesNotContain("decline_code");
        assertThat(response.getMessage()).doesNotContain("req_abc123");
        assertThat(response.getMessage()).doesNotContain("pi_secret_internal");
    }

    @Test
    @DisplayName("should not leak raw exception detail into the gRPC confirmPayment response on exception")
    void should_notLeakExceptionDetail_when_confirmPaymentThrows() {
        when(paymentService.confirmPayment(anyString(), any()))
                .thenThrow(new RuntimeException(RAW_STRIPE_DETAIL));

        service.confirmPayment(
                ConfirmPaymentRequest.newBuilder().setPaymentIntentId(INTENT_ID).build(),
                responseObserver);

        ConfirmPaymentResponse response = captureResponse();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).doesNotContain(RAW_STRIPE_DETAIL);
        assertThat(response.getMessage()).doesNotContain("decline_code");
        assertThat(response.getMessage()).doesNotContain("req_abc123");
        assertThat(response.getMessage()).doesNotContain("pi_secret_internal");
        assertThat(response.getStatus()).isEqualTo(com.ecommerce.paymentservice.grpc.proto.PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("should not leak raw exception detail into the gRPC refundPayment response on exception")
    void should_notLeakExceptionDetail_when_refundPaymentThrows() {
        when(paymentService.refundPayment(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException(RAW_STRIPE_DETAIL));

        service.refundPayment(
                RefundPaymentRequest.newBuilder()
                        .setPaymentIntentId(INTENT_ID)
                        .setAmount(5.0)
                        .setReason("requested_by_customer")
                        .build(),
                refundResponseObserver);

        RefundPaymentResponse response = captureRefundResponse();
        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).doesNotContain(RAW_STRIPE_DETAIL);
        assertThat(response.getMessage()).doesNotContain("decline_code");
        assertThat(response.getMessage()).doesNotContain("req_abc123");
        assertThat(response.getMessage()).doesNotContain("pi_secret_internal");
    }

    private ConfirmPaymentResponse captureResponse() {
        ArgumentCaptor<ConfirmPaymentResponse> captor = ArgumentCaptor.forClass(ConfirmPaymentResponse.class);
        org.mockito.Mockito.verify(responseObserver).onNext(captor.capture());
        org.mockito.Mockito.verify(responseObserver).onCompleted();
        return captor.getValue();
    }

    private CreatePaymentIntentResponse captureCreateResponse() {
        ArgumentCaptor<CreatePaymentIntentResponse> captor =
                ArgumentCaptor.forClass(CreatePaymentIntentResponse.class);
        org.mockito.Mockito.verify(createResponseObserver).onNext(captor.capture());
        org.mockito.Mockito.verify(createResponseObserver).onCompleted();
        return captor.getValue();
    }

    private RefundPaymentResponse captureRefundResponse() {
        ArgumentCaptor<RefundPaymentResponse> captor = ArgumentCaptor.forClass(RefundPaymentResponse.class);
        org.mockito.Mockito.verify(refundResponseObserver).onNext(captor.capture());
        org.mockito.Mockito.verify(refundResponseObserver).onCompleted();
        return captor.getValue();
    }
}
