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

    /**
     * Converge a payment to COMPLETED from the authoritative Stripe webhook
     * ({@code payment_intent.succeeded}) and emit PAYMENT_COMPLETED down the SAME
     * outbox path the client-side confirm uses — so order-service sees one
     * consistent settlement signal regardless of which path fired first.
     *
     * <p>Idempotent convergence: if the client-side confirm already advanced the
     * payment to COMPLETED (or it was refunded), this is a no-op and NO duplicate
     * event is emitted. A payment we have no record of is acknowledged and logged
     * rather than retried, since Stripe would otherwise redeliver indefinitely.
     */
    @Transactional
    public void markPaymentSucceeded(String paymentIntentId, String transactionId) {
        Payment payment = paymentRepository.findByPaymentIntentId(paymentIntentId).orElse(null);
        if (payment == null) {
            log.warn("Webhook succeeded for unknown intent {}; acknowledging without action", paymentIntentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.REFUNDED) {
            log.info("Payment for intent {} already {} — webhook succeeded is a no-op",
                    paymentIntentId, payment.getStatus());
            return;
        }
        payment.setStatus(PaymentStatus.COMPLETED);
        if (transactionId != null) {
            payment.setTransactionId(transactionId);
        }
        paymentRepository.save(payment);
        publishPaymentCompletedEvent(payment);
        log.info("Payment for intent {} reconciled to COMPLETED via webhook", paymentIntentId);
    }

    /**
     * Converge a payment to FAILED from the authoritative Stripe webhook
     * ({@code payment_intent.payment_failed} / {@code payment_intent.canceled})
     * and emit PAYMENT_FAILED down the same outbox path.
     *
     * <p>Idempotent convergence: a payment already FAILED is a no-op; a payment
     * that already settled (COMPLETED/REFUNDED) is NOT downgraded by a late/stray
     * failure event — success wins. An unknown intent is acknowledged and logged.
     */
    @Transactional
    public void markPaymentFailed(String paymentIntentId, String failureReason) {
        Payment payment = paymentRepository.findByPaymentIntentId(paymentIntentId).orElse(null);
        if (payment == null) {
            log.warn("Webhook failed for unknown intent {}; acknowledging without action", paymentIntentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Payment for intent {} already FAILED — webhook failure is a no-op", paymentIntentId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.COMPLETED || payment.getStatus() == PaymentStatus.REFUNDED) {
            log.warn("Ignoring failure webhook for intent {} already in terminal success state {}",
                    paymentIntentId, payment.getStatus());
            return;
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(failureReason);
        paymentRepository.save(payment);
        publishPaymentFailedEvent(payment);
        log.info("Payment for intent {} reconciled to FAILED via webhook", paymentIntentId);
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
