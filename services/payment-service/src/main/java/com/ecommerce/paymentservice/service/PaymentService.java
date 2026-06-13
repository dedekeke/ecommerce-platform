package com.ecommerce.paymentservice.service;

import com.ecommerce.paymentservice.domain.Payment;
import com.ecommerce.paymentservice.domain.PaymentMethod;
import com.ecommerce.paymentservice.domain.PaymentStatus;
import com.ecommerce.paymentservice.gateway.PaymentGatewayResponse;
import com.ecommerce.paymentservice.gateway.PaymentIntentProvider;
import com.ecommerce.paymentservice.kafka.PaymentEvent;
import com.ecommerce.paymentservice.kafka.PaymentEventPublisher;
import com.ecommerce.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentIntentProvider paymentProvider;
    private final PaymentEventPublisher eventPublisher;

    @Transactional
    public Payment createPaymentIntent(String orderId, String userId, BigDecimal amount, String currency) {
        log.info("Creating payment intent for order: {}", orderId);

        // Idempotency guard: a retried checkout for the same order must reuse the existing pending
        // intent rather than creating a second Stripe charge + payment row.
        Payment existing = paymentRepository.findByOrderId(orderId).orElse(null);
        if (existing != null && existing.getStatus() == PaymentStatus.PENDING) {
            log.info("Reusing existing pending payment intent for order: {}", orderId);
            return existing;
        }

        // Call payment gateway to create payment intent
        PaymentGatewayResponse gatewayResponse = paymentProvider.createPaymentIntent(
                orderId, userId, amount, currency);

        // Create payment record
        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .method(PaymentMethod.CREDIT_CARD) // Default for now
                .status(PaymentStatus.PENDING)
                .paymentIntentId(gatewayResponse.getPaymentIntentId())
                .clientSecret(gatewayResponse.getClientSecret())
                .build();

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment confirmPayment(String paymentIntentId, String paymentMethodId) {
        log.info("Confirming payment for intent: {}", paymentIntentId);

        // Find payment by intent ID
        Payment payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for intent: " + paymentIntentId));

        // Update status to processing
        payment.setStatus(PaymentStatus.PROCESSING);
        paymentRepository.save(payment);

        // Call payment gateway to confirm payment
        PaymentGatewayResponse gatewayResponse = paymentProvider.confirmPayment(
                paymentIntentId, paymentMethodId);

        // Update payment based on gateway response
        if (gatewayResponse.isSuccess()) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionId(gatewayResponse.getTransactionId());

            // Publish payment completed event
            publishPaymentCompletedEvent(payment);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(gatewayResponse.getErrorMessage());

            // Publish payment failed event
            publishPaymentFailedEvent(payment);
        }

        return paymentRepository.save(payment);
    }

    @Transactional
    public Payment refundPayment(String paymentIntentId, BigDecimal amount, String reason) {
        log.info("Refunding payment for intent: {}", paymentIntentId);

        // Find payment by intent ID
        Payment payment = paymentRepository.findByPaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new PaymentNotFoundException("Payment not found for intent: " + paymentIntentId));

        // Validate payment can be refunded
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new InvalidPaymentStateException("Payment cannot be refunded. Current status: " + payment.getStatus());
        }

        // Call payment gateway to process refund
        PaymentGatewayResponse gatewayResponse = paymentProvider.refundPayment(
                paymentIntentId, amount, reason);

        if (gatewayResponse.isSuccess()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setTransactionId(gatewayResponse.getTransactionId());
        }

        return paymentRepository.save(payment);
    }

    private void publishPaymentCompletedEvent(Payment payment) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType("PAYMENT_COMPLETED")
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .paymentIntentId(payment.getPaymentIntentId())
                .transactionId(payment.getTransactionId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .timestamp(LocalDateTime.now())
                .build();

        eventPublisher.publishPaymentCompletedEvent(event);
    }

    private void publishPaymentFailedEvent(Payment payment) {
        PaymentEvent event = PaymentEvent.builder()
                .eventType("PAYMENT_FAILED")
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .paymentIntentId(payment.getPaymentIntentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .failureReason(payment.getFailureReason())
                .timestamp(LocalDateTime.now())
                .build();

        eventPublisher.publishPaymentFailedEvent(event);
    }
}
