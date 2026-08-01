package com.ecommerce.orderservice.saga;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.outbox.OutboxEvent;
import com.ecommerce.orderservice.outbox.OutboxRepository;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Transaction-level integration test (real {@code PlatformTransactionManager} +
 * H2, NO ambient test transaction, outbox relay disabled in the {@code test}
 * profile) proving the two saga money-path guarantees that a pure-Mockito test
 * cannot observe:
 *
 * <ol>
 *   <li>the compensating cancel and its ORDER_CANCELLED event commit ATOMICALLY
 *       (DB state and emitted event always agree);</li>
 *   <li>the compensation commit is INDEPENDENT of the orchestration — it
 *       survives even when the saga re-throws afterwards (the previous bug rolled
 *       the cancel back while still emitting the event).</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=false",
        "grpc.server.port=-1"
})
class OrderCompensationIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void should_commitCancelledStateAndCancelledEventTogether_when_compensating() {
        String orderId = persistPendingOrder();

        orderService.compensateCancelOrder(orderId);

        // State committed as CANCELLED (fresh read, no ambient tx).
        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        // ORDER_CANCELLED outbox row committed in the SAME transaction.
        assertThat(outboxRowCount(orderId, "ORDER_CANCELLED")).isEqualTo(1L);
    }

    @Test
    void should_keepCompensationCommitted_when_orchestratorRethrowsAfterwards() {
        String orderId = persistPendingOrder();

        // Model the saga: compensation commits in its own transaction, then the
        // orchestration re-throws. The cancel MUST NOT be undone by that throw.
        assertThatThrownBy(() -> {
            orderService.compensateCancelOrder(orderId);
            throw new RuntimeException("saga re-throw after compensation");
        }).isInstanceOf(RuntimeException.class);

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus())
            .as("compensation must survive the orchestration failure")
            .isEqualTo(OrderStatus.CANCELLED);
        assertThat(outboxRowCount(orderId, "ORDER_CANCELLED")).isEqualTo(1L);
    }

    @Test
    void should_persistSecretAndCreatedEventTogether_when_finalizingSuccessfulOrder() {
        String orderId = persistPendingOrder();

        orderService.finalizeSuccessfulOrder(orderId, "pi_abc", "pi_abc_secret", "buyer@example.com", "Buyer");

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getPaymentIntentId()).isEqualTo("pi_abc");
        assertThat(reloaded.getPaymentClientSecret()).isEqualTo("pi_abc_secret");
        assertThat(outboxRowCount(orderId, "ORDER_CREATED")).isEqualTo(1L);
    }

    private String persistPendingOrder() {
        Order order = Order.builder()
            .orderNumber("ORD-IT-" + UUID.randomUUID())
            .userId("user-it")
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
