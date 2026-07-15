package com.ecommerce.promotionservice.loyalty;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-datastore (H2) tests for {@link OrderCompletedProcessor}.
 *
 * <p>Verifies the exactly-once guarantee at the database level — not just mock
 * sequencing: a duplicate {@code outbox-event-id} fails the ledger INSERT and
 * rolls the whole transaction back, so lifetime spend is counted exactly once.
 *
 * <p>Each test runs with {@link Propagation#NOT_SUPPORTED} so it executes
 * <em>without</em> the usual @DataJpaTest-managed transaction. That lets each
 * {@code process(...)} call open and commit (or roll back) its own real
 * transaction — the very boundary whose rollback we assert. Because those
 * commits persist for the lifetime of the embedded DB, every test uses its own
 * user id and event ids so methods cannot contaminate each other regardless of
 * execution order.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import({LoyaltyService.class, OrderCompletedProcessor.class})
@DisplayName("OrderCompletedProcessor — real-DB exactly-once")
class OrderCompletedProcessorTest {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100.00");

    @Autowired
    private OrderCompletedProcessor processor;

    @Autowired
    private CustomerSpendRepository customerSpendRepository;

    @Autowired
    private ProcessedLoyaltyEventRepository processedEventRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should_recordSpendAndLedgerRow_when_firstDelivery")
    void should_recordSpendAndLedgerRow_when_firstDelivery() {
        processor.process("u-first", ONE_HUNDRED, null, "evt-first");

        assertThat(processedEventRepository.existsById("evt-first")).isTrue();
        assertThat(spendOf("u-first")).isEqualByComparingTo(ONE_HUNDRED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should_countSpendOnceAndRollBack_when_sameEventIdDeliveredTwice")
    void should_countSpendOnceAndRollBack_when_sameEventIdDeliveredTwice() {
        processor.process("u-dup", ONE_HUNDRED, null, "evt-dup");

        // Duplicate delivery: the ledger INSERT hits the primary key, so the
        // whole transaction (including the spend increment) rolls back.
        assertThatThrownBy(() -> processor.process("u-dup", ONE_HUNDRED, null, "evt-dup"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Lifetime spend counted exactly once despite the duplicate delivery.
        assertThat(spendOf("u-dup")).isEqualByComparingTo(ONE_HUNDRED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should_accumulateSpend_when_distinctEventIds")
    void should_accumulateSpend_when_distinctEventIds() {
        processor.process("u-multi", ONE_HUNDRED, null, "evt-a");
        processor.process("u-multi", new BigDecimal("50.00"), null, "evt-b");

        assertThat(spendOf("u-multi")).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("should_notDedupAndAccumulate_when_eventIdNull")
    void should_notDedupAndAccumulate_when_eventIdNull() {
        // No outbox-event-id header -> no ledger row -> cannot dedup. Two
        // deliveries of the same (headerless) event therefore both count.
        processor.process("u-null", new BigDecimal("40.00"), null, null);
        processor.process("u-null", new BigDecimal("40.00"), null, null);

        assertThat(spendOf("u-null")).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    private BigDecimal spendOf(String userId) {
        return customerSpendRepository.findById(userId)
                .map(CustomerSpend::getTotalSpend)
                .orElse(null);
    }
}
