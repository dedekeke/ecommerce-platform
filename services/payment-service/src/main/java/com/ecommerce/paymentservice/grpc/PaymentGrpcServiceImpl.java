package com.ecommerce.paymentservice.grpc;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.grpc.proto.*;
import com.ecommerce.paymentservice.service.PaymentService;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigDecimal;

/**
 * gRPC service implementation for Payment Service
 */
@Slf4j
@GrpcService
@RequiredArgsConstructor
public class PaymentGrpcServiceImpl extends PaymentServiceGrpc.PaymentServiceImplBase {

    private final PaymentService paymentService;

    @Override
    public void createPaymentIntent(CreatePaymentIntentRequest request,
                                    StreamObserver<CreatePaymentIntentResponse> responseObserver) {
        log.info("gRPC: CreatePaymentIntent request for order: {}", request.getOrderId());

        try {
            Payment payment = paymentService.createPaymentIntent(
                    request.getOrderId(),
                    request.getUserId(),
                    BigDecimal.valueOf(request.getAmount()),
                    request.getCurrency()
            );

            CreatePaymentIntentResponse response = CreatePaymentIntentResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Payment intent created successfully")
                    .setPaymentIntentId(payment.getPaymentIntentId())
                    .setClientSecret(payment.getClientSecret())
                    .setStatus(convertToPaymentStatusProto(payment.getStatus()))
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC: Payment intent created successfully: {}", payment.getPaymentIntentId());

        } catch (Exception e) {
            log.error("gRPC: Error creating payment intent", e);
            CreatePaymentIntentResponse response = CreatePaymentIntentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to create payment intent: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void confirmPayment(ConfirmPaymentRequest request,
                               StreamObserver<ConfirmPaymentResponse> responseObserver) {
        log.info("gRPC: ConfirmPayment request for intent: {}", request.getPaymentIntentId());

        try {
            Payment payment = paymentService.confirmPayment(
                    request.getPaymentIntentId(),
                    request.getPaymentMethodId()
            );

            boolean isSuccess = payment.getStatus() == com.ecommerce.paymentservice.domain.PaymentStatus.COMPLETED;

            ConfirmPaymentResponse.Builder responseBuilder = ConfirmPaymentResponse.newBuilder()
                    .setSuccess(isSuccess)
                    .setStatus(convertToPaymentStatusProto(payment.getStatus()));

            if (isSuccess) {
                responseBuilder.setMessage("Payment confirmed successfully");
                responseBuilder.setTransactionId(payment.getTransactionId());
            } else {
                responseBuilder.setMessage("Payment failed: " +
                    (payment.getFailureReason() != null ? payment.getFailureReason() : "Unknown error"));
            }

            ConfirmPaymentResponse response = responseBuilder.build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC: Payment confirmation completed with status: {}", payment.getStatus());

        } catch (Exception e) {
            log.error("gRPC: Error confirming payment", e);
            ConfirmPaymentResponse response = ConfirmPaymentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to confirm payment: " + e.getMessage())
                    .setStatus(com.ecommerce.paymentservice.grpc.proto.PaymentStatus.FAILED)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void refundPayment(RefundPaymentRequest request,
                             StreamObserver<RefundPaymentResponse> responseObserver) {
        log.info("gRPC: RefundPayment request for intent: {}", request.getPaymentIntentId());

        try {
            Payment payment = paymentService.refundPayment(
                    request.getPaymentIntentId(),
                    BigDecimal.valueOf(request.getAmount()),
                    request.getReason()
            );

            RefundPaymentResponse response = RefundPaymentResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Payment refunded successfully")
                    .setRefundId(payment.getTransactionId())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

            log.info("gRPC: Payment refunded successfully: {}", payment.getPaymentIntentId());

        } catch (Exception e) {
            log.error("gRPC: Error refunding payment", e);
            RefundPaymentResponse response = RefundPaymentResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Failed to refund payment: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    // Conversion methods
    private com.ecommerce.paymentservice.grpc.proto.PaymentStatus convertToPaymentStatusProto(
            com.ecommerce.paymentservice.domain.PaymentStatus status) {
        return switch (status) {
            case PENDING -> com.ecommerce.paymentservice.grpc.proto.PaymentStatus.PENDING;
            case PROCESSING -> com.ecommerce.paymentservice.grpc.proto.PaymentStatus.PROCESSING;
            case COMPLETED -> com.ecommerce.paymentservice.grpc.proto.PaymentStatus.COMPLETED;
            case FAILED -> com.ecommerce.paymentservice.grpc.proto.PaymentStatus.FAILED;
            case REFUNDED -> com.ecommerce.paymentservice.grpc.proto.PaymentStatus.REFUNDED;
        };
    }
}
