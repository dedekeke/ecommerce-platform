package com.ecommerce.orderservice.saga.refund.step;

import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentRequest;
import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentResponse;
import com.ecommerce.orderservice.grpc.proto.payment.PaymentServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.payment.RefundPaymentRequest;
import com.ecommerce.orderservice.grpc.proto.payment.RefundPaymentResponse;
import com.ecommerce.orderservice.saga.refund.RefundSagaContext;
import com.ecommerce.orderservice.saga.refund.StepResult;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReversePaymentStepTest {

    private final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    private MockPaymentService mockPaymentService;
    private ManagedChannel channel;
    private ReversePaymentStep step;

    @BeforeEach
    void setUp() throws Exception {
        mockPaymentService = new MockPaymentService();
        String name = InProcessServerBuilder.generateName();
        grpcCleanup.register(InProcessServerBuilder.forName(name)
            .directExecutor().addService(mockPaymentService).build().start());
        channel = grpcCleanup.register(
            InProcessChannelBuilder.forName(name).directExecutor().build());

        step = new ReversePaymentStep();
        step.paymentServiceStub = PaymentServiceGrpc.newBlockingStub(channel);
    }

    @Test
    void should_succeed_and_setRefundTransactionId() {
        mockPaymentService.refundSuccess = true;
        mockPaymentService.refundId = "ref-1";

        RefundSagaContext ctx = RefundSagaContext.builder()
            .orderId("o1").paymentIntentId("pi_123")
            .refundAmount(new BigDecimal("50.00")).reason("changed mind").build();

        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isTrue();
        assertThat(ctx.getRefundTransactionId()).isEqualTo("ref-1");
        assertThat(mockPaymentService.lastRefundRequest.getPaymentIntentId()).isEqualTo("pi_123");
    }

    @Test
    void should_fail_when_paymentIntentMissing() {
        StepResult result = step.execute(RefundSagaContext.builder().build());

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("payment intent");
    }

    @Test
    void should_fail_when_paymentServiceReturnsFailure() {
        mockPaymentService.refundSuccess = false;
        mockPaymentService.failureMessage = "card not refundable";

        RefundSagaContext ctx = RefundSagaContext.builder()
            .paymentIntentId("pi_x").refundAmount(BigDecimal.TEN).build();
        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("card not refundable");
    }

    @Test
    void compensate_should_invokeCreatePaymentIntent_whenRefundHadHappened() {
        RefundSagaContext ctx = RefundSagaContext.builder()
            .orderId("o1").userId("u1")
            .refundAmount(new BigDecimal("25.00"))
            .refundTransactionId("ref-9")
            .build();
        step.compensate(ctx);

        assertThat(mockPaymentService.lastCreateIntentRequest).isNotNull();
        assertThat(mockPaymentService.lastCreateIntentRequest.getOrderId()).isEqualTo("o1");
    }

    @Test
    void compensate_should_skip_whenNoRefundTransactionId() {
        step.compensate(RefundSagaContext.builder().build());
        assertThat(mockPaymentService.lastCreateIntentRequest).isNull();
    }

    @Test
    void execute_should_returnFailure_whenStubThrows() {
        // Force a runtime exception path by using an invalid stub.
        ReversePaymentStep brokenStep = new ReversePaymentStep();
        brokenStep.paymentServiceStub = null;

        RefundSagaContext ctx = RefundSagaContext.builder()
            .paymentIntentId("pi_x").refundAmount(java.math.BigDecimal.ONE).build();
        StepResult result = brokenStep.execute(ctx);

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("Payment service error");
    }

    @Test
    void compensate_should_handle_grpcException_silently() {
        ReversePaymentStep brokenStep = new ReversePaymentStep();
        brokenStep.paymentServiceStub = null;

        // Should not throw despite null stub.
        brokenStep.compensate(RefundSagaContext.builder()
            .refundTransactionId("ref-9").orderId("o1").build());
    }

    @Test
    void execute_should_useEmptyReason_whenContextHasNone() {
        mockPaymentService.refundSuccess = true;
        RefundSagaContext ctx = RefundSagaContext.builder()
            .paymentIntentId("pi").refundAmount(null).build();

        step.execute(ctx);

        assertThat(mockPaymentService.lastRefundRequest.getReason()).isEqualTo("Customer refund");
        assertThat(mockPaymentService.lastRefundRequest.getAmount()).isEqualTo(0d);
    }

    private static class MockPaymentService extends PaymentServiceGrpc.PaymentServiceImplBase {
        boolean refundSuccess = true;
        String refundId = "default-ref";
        String failureMessage = "fail";
        RefundPaymentRequest lastRefundRequest;
        CreatePaymentIntentRequest lastCreateIntentRequest;

        @Override
        public void refundPayment(RefundPaymentRequest request,
            StreamObserver<RefundPaymentResponse> responseObserver) {
            lastRefundRequest = request;
            responseObserver.onNext(RefundPaymentResponse.newBuilder()
                .setSuccess(refundSuccess)
                .setMessage(refundSuccess ? "ok" : failureMessage)
                .setRefundId(refundSuccess ? refundId : "")
                .build());
            responseObserver.onCompleted();
        }

        @Override
        public void createPaymentIntent(CreatePaymentIntentRequest request,
            StreamObserver<CreatePaymentIntentResponse> responseObserver) {
            lastCreateIntentRequest = request;
            responseObserver.onNext(CreatePaymentIntentResponse.newBuilder()
                .setSuccess(true).setPaymentIntentId("pi-comp").build());
            responseObserver.onCompleted();
        }
    }
}
