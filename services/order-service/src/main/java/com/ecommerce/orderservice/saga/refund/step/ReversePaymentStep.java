package com.ecommerce.orderservice.saga.refund.step;

import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentRequest;
import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentResponse;
import com.ecommerce.orderservice.grpc.proto.payment.PaymentServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.payment.RefundPaymentRequest;
import com.ecommerce.orderservice.grpc.proto.payment.RefundPaymentResponse;
import com.ecommerce.orderservice.saga.refund.RefundSagaContext;
import com.ecommerce.orderservice.saga.refund.RefundSagaStep;
import com.ecommerce.orderservice.saga.refund.SagaStep;
import com.ecommerce.orderservice.saga.refund.StepResult;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReversePaymentStep implements SagaStep {

    @GrpcClient("payment-service")
    PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    @Override
    public RefundSagaStep id() {
        return RefundSagaStep.REVERSE_PAYMENT;
    }

    @Override
    public boolean hasCompensation() {
        return true;
    }

    @Override
    public StepResult execute(RefundSagaContext ctx) {
        if (ctx.getPaymentIntentId() == null || ctx.getPaymentIntentId().isBlank()) {
            return StepResult.failure("No payment intent on order — cannot reverse");
        }
        try {
            RefundPaymentRequest request = RefundPaymentRequest.newBuilder()
                .setPaymentIntentId(ctx.getPaymentIntentId())
                .setAmount(ctx.getRefundAmount() == null ? 0d : ctx.getRefundAmount().doubleValue())
                .setReason(ctx.getReason() == null ? "Customer refund" : ctx.getReason())
                .build();
            RefundPaymentResponse response = paymentServiceStub.refundPayment(request);
            if (!response.getSuccess()) {
                return StepResult.failure(
                    "Payment refund failed: " + response.getMessage());
            }
            ctx.setRefundTransactionId(response.getRefundId());
            log.info("Payment reversed: refundId={} for paymentIntent={}",
                response.getRefundId(), ctx.getPaymentIntentId());
            return StepResult.ok();
        } catch (Exception e) {
            log.error("Error calling payment-service refundPayment", e);
            return StepResult.failure("Payment service error: " + e.getMessage());
        }
    }

    /**
     * Compensation: re-charge the customer by creating a new payment intent
     * for the same amount. This is best-effort — in production you'd more
     * likely flag the order for manual review than auto re-charge.
     */
    @Override
    public void compensate(RefundSagaContext ctx) {
        if (ctx.getRefundTransactionId() == null) {
            log.debug("No refund transaction to reverse — skipping compensation");
            return;
        }
        try {
            CreatePaymentIntentRequest request = CreatePaymentIntentRequest.newBuilder()
                .setOrderId(ctx.getOrderId())
                .setUserId(ctx.getUserId() == null ? "" : ctx.getUserId())
                .setAmount(ctx.getRefundAmount() == null ? 0d : ctx.getRefundAmount().doubleValue())
                .setCurrency("USD")
                .build();
            CreatePaymentIntentResponse response = paymentServiceStub.createPaymentIntent(request);
            log.warn("Compensating refund {} via re-charge intent {} (success={})",
                ctx.getRefundTransactionId(), response.getPaymentIntentId(), response.getSuccess());
        } catch (Exception e) {
            log.error("Failed to compensate payment reversal for saga {}",
                ctx.getState() != null ? ctx.getState().getId() : "?", e);
        }
    }
}
