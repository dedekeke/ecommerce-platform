package com.ecommerce.orderservice.payment;

import com.ecommerce.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional application of payment settlement events to the order state
 * machine. Kept free of Kafka/gRPC plumbing so it is exercised directly against
 * a real transaction manager (see {@code PaymentEventHandlerIntegrationTest}).
 *
 * <p>Idempotency + convergence are enforced in two layers:
 * <ol>
 *   <li>{@link ProcessedPaymentEvent} dedup on the producer's event id — the
 *       marker is written in the SAME transaction as the state change, so a
 *       redelivery is applied at most once;</li>
 *   <li>the precedence rules in {@link OrderService#confirmOrderPaid} /
 *       {@link OrderService#failOrderPayment} — a duplicate that slips past
 *       layer 1 (or an out-of-band status change, e.g. the admin status
 *       endpoint, or a future client-confirm path) still converges to the same
 *       terminal state and never downgrades a paid order.</li>
 * </ol>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventHandler {

    private final OrderService orderService;
    private final ProcessedPaymentEventRepository processedRepository;

    static final String TYPE_COMPLETED = "PAYMENT_COMPLETED";
    static final String TYPE_FAILED = "PAYMENT_FAILED";

    /**
     * Apply a {@code payment.completed} event: advance the order to CONFIRMED.
     */
    @Transactional
    public void onPaymentCompleted(String eventId, PaymentEventEnvelope event) {
        if (!claim(eventId, TYPE_COMPLETED, event.orderId())) {
            return;
        }
        orderService.confirmOrderPaid(event.orderId());
    }

    /**
     * Apply a {@code payment.failed} event: cancel a still-PENDING order.
     *
     * @return {@code true} iff the caller must release the inventory reservation
     *         (i.e. this delivery actually cancelled a PENDING order)
     */
    @Transactional
    public boolean onPaymentFailed(String eventId, PaymentEventEnvelope event) {
        if (!claim(eventId, TYPE_FAILED, event.orderId())) {
            return false;
        }
        return orderService.failOrderPayment(event.orderId());
    }

    /**
     * Atomically claim this event id. Returns {@code true} on first sight (and
     * records the marker in the current transaction), {@code false} on a
     * duplicate. A missing id (undecorated producer / test fixture) is processed
     * without dedup rather than dropped.
     */
    private boolean claim(String eventId, String type, String orderId) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("Payment event without id (type={}, order={}) — processing without dedup",
                type, orderId);
            return true;
        }
        if (processedRepository.existsById(eventId)) {
            log.info("Duplicate payment event {} (type={}, order={}) — skipping", eventId, type, orderId);
            return false;
        }
        processedRepository.save(ProcessedPaymentEvent.of(eventId, type, orderId));
        return true;
    }
}
