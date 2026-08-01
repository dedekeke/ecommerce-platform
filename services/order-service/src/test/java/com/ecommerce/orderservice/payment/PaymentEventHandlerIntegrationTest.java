package com.ecommerce.orderservice.payment;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.outbox.OutboxEvent;
import com.ecommerce.orderservice.outbox.OutboxRepository;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transaction-level integration test (real transaction manager + H2, NO ambient
 * test transaction, outbox relay disabled) for the payment-event → order state
 * machine. Proves the money-path guarantees the consumer must uphold:
 * advancing to PAID, idempotent dedup, reservation-release signalling on
 * failure, and the precedence rules (no downgrade of a paid order, no
 * resurrection of a cancelled one).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=false",
        "grpc.server.port=-1"
})
class PaymentEventHandlerIntegrationTest {

    @Autowired
    private PaymentEventHandler handler;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private ProcessedPaymentEventRepository processedRepository;

    @Test
    void should_advanceOrderToConfirmed_when_paymentCompleted() {
        String orderId = persistPendingOrder();

        handler.onPaymentCompleted("evt-" + UUID.randomUUID(), completedFor(orderId));

        assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(outboxRowCount(orderId, "ORDER_UPDATED")).isEqualTo(1L);
    }

    @Test
    void should_applyOnce_when_paymentCompletedDeliveredTwice() {
        String orderId = persistPendingOrder();
        String eventId = "evt-" + UUID.randomUUID();

        handler.onPaymentCompleted(eventId, completedFor(orderId));
        handler.onPaymentCompleted(eventId, completedFor(orderId));

        assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        // Dedup: exactly one processed marker and one ORDER_UPDATED event.
        assertThat(processedRepository.existsById(eventId)).isTrue();
        assertThat(outboxRowCount(orderId, "ORDER_UPDATED")).isEqualTo(1L);
    }

    @Test
    void should_cancelOrderAndSignalRelease_when_paymentFailed() {
        String orderId = persistPendingOrder();

        boolean releaseNeeded = handler.onPaymentFailed("evt-" + UUID.randomUUID(), failedFor(orderId));

        assertThat(releaseNeeded).isTrue();
        assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxRowCount(orderId, "ORDER_CANCELLED")).isEqualTo(1L);
    }

    @Test
    void should_notSignalReleaseAgain_when_paymentFailedDeliveredTwice() {
        String orderId = persistPendingOrder();
        String eventId = "evt-" + UUID.randomUUID();

        assertThat(handler.onPaymentFailed(eventId, failedFor(orderId))).isTrue();
        // Duplicate delivery: deduped, so no second cancel and no second release.
        assertThat(handler.onPaymentFailed(eventId, failedFor(orderId))).isFalse();

        assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxRowCount(orderId, "ORDER_CANCELLED")).isEqualTo(1L);
    }

    @Test
    void should_notDowngrade_when_paymentFailedArrivesAfterPaid() {
        String orderId = persistPendingOrder();
        handler.onPaymentCompleted("evt-" + UUID.randomUUID(), completedFor(orderId));

        boolean releaseNeeded = handler.onPaymentFailed("evt-" + UUID.randomUUID(), failedFor(orderId));

        assertThat(releaseNeeded).as("a stale failure must not release a paid order").isFalse();
        assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(outboxRowCount(orderId, "ORDER_CANCELLED")).isZero();
    }

    @Test
    void should_notResurrect_when_paymentCompletedArrivesAfterCancelled() {
        String orderId = persistPendingOrder();
        handler.onPaymentFailed("evt-" + UUID.randomUUID(), failedFor(orderId));

        handler.onPaymentCompleted("evt-" + UUID.randomUUID(), completedFor(orderId));

        assertThat(reload(orderId).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxRowCount(orderId, "ORDER_UPDATED")).isZero();
    }

    @Test
    void should_ackAndNotThrow_when_orderIdUnknown() {
        String unknownOrderId = "missing-" + UUID.randomUUID();
        String eventId = "evt-" + UUID.randomUUID();

        handler.onPaymentCompleted(eventId, completedFor(unknownOrderId));

        // Marked processed (acked, no infinite retry); nothing created.
        assertThat(processedRepository.existsById(eventId)).isTrue();
        assertThat(orderRepository.findById(unknownOrderId)).isEmpty();
    }

    private PaymentEventEnvelope completedFor(String orderId) {
        return new PaymentEventEnvelope("PAYMENT_COMPLETED", orderId, "pi_" + orderId, "COMPLETED", null);
    }

    private PaymentEventEnvelope failedFor(String orderId) {
        return new PaymentEventEnvelope("PAYMENT_FAILED", orderId, "pi_" + orderId, "FAILED", "card_declined");
    }

    private Order reload(String orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }

    private String persistPendingOrder() {
        Order order = Order.builder()
            .orderNumber("ORD-PE-" + UUID.randomUUID())
            .userId("user-pe")
            .subtotal(BigDecimal.ZERO)
            .tax(new BigDecimal("4.00"))
            .shippingCost(BigDecimal.ZERO)
            .total(new BigDecimal("4.00"))
            .status(OrderStatus.PENDING)
            .shippingAddress(Address.builder()
                .street("1 Main St").city("SF").state("CA")
                .postalCode("94105").country("USA").build())
            .build();
        return orderRepository.save(order).getId();
    }

    private long outboxRowCount(String aggregateId, String eventType) {
        return outboxRepository.findAll().stream()
            .filter(e -> aggregateId.equals(e.getAggregateId()))
            .filter(e -> eventType.equals(e.getEventType()))
            .map(OutboxEvent::getEventId)
            .distinct()
            .count();
    }
}
