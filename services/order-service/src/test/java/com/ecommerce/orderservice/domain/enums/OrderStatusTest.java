package com.ecommerce.orderservice.domain.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OrderStatus state machine
 */
class OrderStatusTest {

    @Test
    void testPendingTransitions() {
        OrderStatus pending = OrderStatus.PENDING;

        // Valid transitions
        assertTrue(pending.canTransitionTo(OrderStatus.CONFIRMED));
        assertTrue(pending.canTransitionTo(OrderStatus.CANCELLED));

        // Invalid transitions
        assertFalse(pending.canTransitionTo(OrderStatus.PROCESSING));
        assertFalse(pending.canTransitionTo(OrderStatus.SHIPPED));
        assertFalse(pending.canTransitionTo(OrderStatus.DELIVERED));
        assertFalse(pending.canTransitionTo(OrderStatus.REFUNDED));
    }

    @Test
    void testConfirmedTransitions() {
        OrderStatus confirmed = OrderStatus.CONFIRMED;

        // Valid transitions — a paid order may ship directly, or via PROCESSING.
        assertTrue(confirmed.canTransitionTo(OrderStatus.PROCESSING));
        assertTrue(confirmed.canTransitionTo(OrderStatus.SHIPPED));
        assertTrue(confirmed.canTransitionTo(OrderStatus.CANCELLED));

        // Invalid transitions
        assertFalse(confirmed.canTransitionTo(OrderStatus.PENDING));
        assertFalse(confirmed.canTransitionTo(OrderStatus.DELIVERED));
        assertFalse(confirmed.canTransitionTo(OrderStatus.REFUNDED));
    }

    @Test
    void testProcessingTransitions() {
        OrderStatus processing = OrderStatus.PROCESSING;

        // Valid transitions
        assertTrue(processing.canTransitionTo(OrderStatus.SHIPPED));
        assertTrue(processing.canTransitionTo(OrderStatus.CANCELLED));

        // Invalid transitions
        assertFalse(processing.canTransitionTo(OrderStatus.PENDING));
        assertFalse(processing.canTransitionTo(OrderStatus.CONFIRMED));
        assertFalse(processing.canTransitionTo(OrderStatus.DELIVERED));
        assertFalse(processing.canTransitionTo(OrderStatus.REFUNDED));
    }

    @Test
    void testShippedTransitions() {
        OrderStatus shipped = OrderStatus.SHIPPED;

        // Valid transitions
        assertTrue(shipped.canTransitionTo(OrderStatus.DELIVERED));

        // Invalid transitions
        assertFalse(shipped.canTransitionTo(OrderStatus.PENDING));
        assertFalse(shipped.canTransitionTo(OrderStatus.CONFIRMED));
        assertFalse(shipped.canTransitionTo(OrderStatus.PROCESSING));
        assertFalse(shipped.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(shipped.canTransitionTo(OrderStatus.REFUNDED));
    }

    @Test
    void testDeliveredTransitions() {
        OrderStatus delivered = OrderStatus.DELIVERED;

        // Valid transitions
        assertTrue(delivered.canTransitionTo(OrderStatus.REFUNDED));

        // Invalid transitions
        assertFalse(delivered.canTransitionTo(OrderStatus.PENDING));
        assertFalse(delivered.canTransitionTo(OrderStatus.CONFIRMED));
        assertFalse(delivered.canTransitionTo(OrderStatus.PROCESSING));
        assertFalse(delivered.canTransitionTo(OrderStatus.SHIPPED));
        assertFalse(delivered.canTransitionTo(OrderStatus.CANCELLED));
    }

    @Test
    void testTerminalStates() {
        // Terminal states should not allow any transitions
        OrderStatus cancelled = OrderStatus.CANCELLED;
        OrderStatus refunded = OrderStatus.REFUNDED;

        assertTrue(cancelled.getAllowedTransitions().isEmpty());
        assertTrue(refunded.getAllowedTransitions().isEmpty());

        assertTrue(cancelled.isTerminal());
        assertTrue(refunded.isTerminal());
        assertTrue(OrderStatus.DELIVERED.isTerminal());
    }

    @Test
    void testCancellableStates() {
        assertTrue(OrderStatus.PENDING.isCancellable());
        assertTrue(OrderStatus.CONFIRMED.isCancellable());
        assertTrue(OrderStatus.PROCESSING.isCancellable());

        assertFalse(OrderStatus.SHIPPED.isCancellable());
        assertFalse(OrderStatus.DELIVERED.isCancellable());
        assertFalse(OrderStatus.CANCELLED.isCancellable());
        assertFalse(OrderStatus.REFUNDED.isCancellable());
    }

    @Test
    void testRefundableStates() {
        assertTrue(OrderStatus.DELIVERED.isRefundable());

        assertFalse(OrderStatus.PENDING.isRefundable());
        assertFalse(OrderStatus.CONFIRMED.isRefundable());
        assertFalse(OrderStatus.PROCESSING.isRefundable());
        assertFalse(OrderStatus.SHIPPED.isRefundable());
        assertFalse(OrderStatus.CANCELLED.isRefundable());
        assertFalse(OrderStatus.REFUNDED.isRefundable());
    }

    @Test
    void testCompleteOrderFlow() {
        // Test the complete order lifecycle
        OrderStatus status = OrderStatus.PENDING;

        // PENDING -> CONFIRMED
        assertTrue(status.canTransitionTo(OrderStatus.CONFIRMED));
        status = OrderStatus.CONFIRMED;

        // CONFIRMED -> PROCESSING
        assertTrue(status.canTransitionTo(OrderStatus.PROCESSING));
        status = OrderStatus.PROCESSING;

        // PROCESSING -> SHIPPED
        assertTrue(status.canTransitionTo(OrderStatus.SHIPPED));
        status = OrderStatus.SHIPPED;

        // SHIPPED -> DELIVERED
        assertTrue(status.canTransitionTo(OrderStatus.DELIVERED));
        status = OrderStatus.DELIVERED;

        // DELIVERED -> REFUNDED
        assertTrue(status.canTransitionTo(OrderStatus.REFUNDED));
        status = OrderStatus.REFUNDED;

        // REFUNDED is terminal
        assertTrue(status.isTerminal());
        assertTrue(status.getAllowedTransitions().isEmpty());
    }

    @Test
    void testCancellationFlow() {
        // Test cancellation from different states
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED));
        assertTrue(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED));
        assertTrue(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.CANCELLED));

        // Cannot cancel after shipped
        assertFalse(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED));
        assertFalse(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED));
    }
}
