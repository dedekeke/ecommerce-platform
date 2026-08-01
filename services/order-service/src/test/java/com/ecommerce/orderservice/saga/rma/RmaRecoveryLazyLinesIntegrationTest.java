package com.ecommerce.orderservice.saga.rma;

import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.saga.refund.RefundOrchestrator;
import com.ecommerce.orderservice.saga.refund.RefundSagaState;
import com.ecommerce.orderservice.saga.refund.RefundSagaStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * Regression test for the RMA recovery LazyInitializationException (task b934f19e).
 *
 * <p>Reproduces the production bug: the recovery scheduler reads stuck returns
 * in one transaction, that transaction commits/closes, and then {@code resume()}
 * -> {@code handleApproved()} touches {@code rma.getLines()} on a now-detached
 * entity. With {@code Return.lines} mapped {@code LAZY}, the old non-fetching
 * query left the collection uninitialized, so {@code getLines()} threw
 * {@code LazyInitializationException} for any INSPECTING/APPROVED partial return
 * that HAS lines.</p>
 *
 * <p>We use {@link TransactionTemplate} to commit the seed and run the
 * scheduler's repository read in their own transactions, so the persistence
 * context is genuinely closed before the lines are accessed — exactly as in
 * the scheduled path. The real {@link RmaOrchestrator} + {@link ReturnRepository}
 * (H2) run; only the non-JPA collaborators are mocked.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Import(RmaOrchestrator.class)
// Suppress @DataJpaTest's surrounding transaction so each @Transactional
// boundary (the scheduler's repo read, then orchestrator.resume()) opens and
// closes its own persistence context — the only way the detachment that causes
// the production LazyInitializationException actually reproduces.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("RMA recovery — lazy lines regression")
class RmaRecoveryLazyLinesIntegrationTest {

    @Autowired private ReturnRepository returnRepository;
    @Autowired private RmaOrchestrator orchestrator;
    @Autowired private TransactionTemplate txTemplate;
    @Autowired private EntityManager entityManager;

    private static final LocalDateTime STUCK_SINCE = LocalDateTime.parse("2026-04-29T09:00:00");

    @MockBean private OrderRepository orderRepository;
    @MockBean private RefundOrchestrator refundOrchestrator;
    @MockBean private MockShippingClient shippingClient;
    @MockBean private RmaEventPublisher eventPublisher;
    @MockBean private Clock clock;

    private RmaRecoveryScheduler scheduler;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC);
        lenient().when(clock.instant()).thenReturn(fixed.instant());
        lenient().when(clock.getZone()).thenReturn(fixed.getZone());

        lenient().when(orderRepository.findById(anyString())).thenReturn(java.util.Optional.empty());
        lenient().when(refundOrchestrator.startRefund(anyString(), anyString(), any(), any(), any()))
            .thenReturn(RefundSagaState.builder()
                .id("refund-1")
                .orderId("order-1")
                .status(RefundSagaStatus.COMPLETED)
                .build());

        scheduler = new RmaRecoveryScheduler(returnRepository, orchestrator, clock);
    }

    @Test
    @DisplayName("should_notThrowLazyInit_andApproveLines_when_recoveringStuckInspectingReturnWithLines")
    void should_notThrowLazyInit_andApproveLines_when_recoveringStuckInspectingReturnWithLines() {
        String rmaId = seedStuckInspectingReturnWithLines();

        // No surrounding tx: the scheduler's repo read commits and detaches the
        // entity, then resume() -> handleApproved() touches getLines(). Without
        // the JOIN FETCH this throws LazyInitializationException.
        assertThatCode(() -> scheduler.recoverStuckRmas()).doesNotThrowAnyException();

        Return reloaded = returnRepository.findByIdWithLines(rmaId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ReturnStatus.COMPLETED);
        assertThat(reloaded.getLines()).hasSize(2);
        assertThat(reloaded.getLines()).allMatch(ReturnLine::isApproved);
        // Approved-line total drives the refund base: 2*30 + 1*10 = 70.00
        assertThat(reloaded.approvedLinesTotal()).isEqualByComparingTo("70.00");
    }

    @Test
    @DisplayName("fetchQuery_should_returnDetachedReturnWithInitializedLines")
    void fetchQuery_should_returnDetachedReturnWithInitializedLines() {
        String rmaId = seedStuckInspectingReturnWithLines();

        // Read tx opens and closes inside this call (NOT_SUPPORTED at class level).
        List<Return> stuck = returnRepository.findByStatusInAndUpdatedAtBeforeWithLines(
            RmaRecoveryScheduler.RECOVERABLE_STATUSES,
            LocalDateTime.parse("2026-04-29T09:55:00"));

        Return seeded = stuck.stream().filter(r -> r.getId().equals(rmaId)).findFirst().orElseThrow();
        // Persistence context for the read is closed; accessing lines on the
        // detached entity must not raise LazyInitializationException.
        assertThatCode(() -> assertThat(seeded.getLines()).hasSize(2))
            .doesNotThrowAnyException();
    }

    private String seedStuckInspectingReturnWithLines() {
        return txTemplate.execute(s -> {
            Return rma = Return.builder()
                .rmaNumber("RMA-STUCK-" + System.nanoTime())
                .orderId("order-1")
                .userId("user-1")
                .status(ReturnStatus.INSPECTING)
                .outcome(RmaOrchestrator.OUTCOME_APPROVED)
                .reason("defect")
                .lines(new java.util.ArrayList<>())
                .build();
            rma.addLine(line("item-1", 2, new BigDecimal("30.00")));
            rma.addLine(line("item-2", 1, new BigDecimal("10.00")));
            String id = returnRepository.save(rma).getId();
            // @UpdateTimestamp stamps updatedAt with wall-clock now on save, so
            // force it back behind the recovery cutoff with a bulk update.
            entityManager.flush();
            entityManager.createQuery("UPDATE Return r SET r.updatedAt = :ts WHERE r.id = :id")
                .setParameter("ts", STUCK_SINCE)
                .setParameter("id", id)
                .executeUpdate();
            return id;
        });
    }

    private ReturnLine line(String itemId, int qty, BigDecimal unitPrice) {
        return ReturnLine.builder()
            .orderItemId(itemId)
            .productId("prod-" + itemId)
            .quantity(qty)
            .unitPrice(unitPrice)
            .reason("r")
            .approved(false)
            .build();
    }
}
