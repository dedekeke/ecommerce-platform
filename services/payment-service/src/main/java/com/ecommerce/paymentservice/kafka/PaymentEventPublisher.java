package com.ecommerce.paymentservice.kafka;

import com.ecommerce.paymentservice.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Publisher for payment-related events.
 *
 * <p>Refactored to use the transactional outbox. The public API stays the
 * same so {@link com.ecommerce.paymentservice.service.PaymentService} can
 * call it without changes — but instead of dual-writing to Kafka, we record
 * the event in the {@code outbox_event} table inside the caller's existing
 * {@code @Transactional} method. The {@link
 * com.ecommerce.paymentservice.outbox.OutboxRelay} ships rows to Kafka
 * asynchronously.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final OutboxService outboxService;

    private static final String AGGREGATE_TYPE = "Payment";

    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    public void publishPaymentCompletedEvent(PaymentEvent event) {
        outboxService.recordEvent(
            AGGREGATE_TYPE,
            event.getOrderId(),
            "PAYMENT_COMPLETED",
            PAYMENT_COMPLETED_TOPIC,
            event
        );
        log.info("Recorded PAYMENT_COMPLETED outbox event for order: {}", event.getOrderId());
    }

    public void publishPaymentFailedEvent(PaymentEvent event) {
        outboxService.recordEvent(
            AGGREGATE_TYPE,
            event.getOrderId(),
            "PAYMENT_FAILED",
            PAYMENT_FAILED_TOPIC,
            event
        );
        log.info("Recorded PAYMENT_FAILED outbox event for order: {}", event.getOrderId());
    }
}
