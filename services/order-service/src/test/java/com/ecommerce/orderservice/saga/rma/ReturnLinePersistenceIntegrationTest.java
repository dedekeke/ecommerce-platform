package com.ecommerce.orderservice.saga.rma;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the partial-returns mapping (§3.8) against embedded H2.
 * Verifies the {@code Return} 1:N {@code ReturnLine} cascade persists, the
 * restocking fee column round-trips, and {@code approvedLinesTotal()} sums
 * only approved lines.
 */
@DataJpaTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@DisplayName("Return / ReturnLine persistence")
class ReturnLinePersistenceIntegrationTest {

    @Autowired private ReturnRepository returnRepository;
    @Autowired private ReturnLineRepository returnLineRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("should_cascadePersistLines_andRoundTripRestockingFee")
    void should_cascadePersistLines_andRoundTripRestockingFee() {
        Return rma = baseReturn();
        rma.setRestockingFeePercent(new BigDecimal("15.00"));
        rma.addLine(line("item-1", 2, new BigDecimal("30.00"), true));
        rma.addLine(line("item-2", 1, new BigDecimal("10.00"), false));

        Return saved = returnRepository.save(rma);
        entityManager.flush();
        entityManager.clear();

        Return reloaded = returnRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getRestockingFeePercent()).isEqualByComparingTo("15.00");
        assertThat(reloaded.getLines()).hasSize(2);
        assertThat(returnLineRepository.findByReturnRequestId(saved.getId())).hasSize(2);
    }

    @Test
    @DisplayName("approvedLinesTotal_should_sumOnlyApprovedLines")
    void approvedLinesTotal_should_sumOnlyApprovedLines() {
        Return rma = baseReturn();
        rma.addLine(line("item-1", 2, new BigDecimal("30.00"), true));   // 60.00 approved
        rma.addLine(line("item-2", 1, new BigDecimal("10.00"), false));  // not approved
        rma.addLine(line("item-3", 3, new BigDecimal("5.00"), true));    // 15.00 approved

        Return saved = returnRepository.save(rma);
        entityManager.flush();
        entityManager.clear();

        Return reloaded = returnRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.approvedLinesTotal()).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("orphanRemoval_should_deleteLines_when_clearedFromParent")
    void orphanRemoval_should_deleteLines_when_clearedFromParent() {
        Return rma = baseReturn();
        rma.addLine(line("item-1", 1, new BigDecimal("9.99"), false));
        Return saved = returnRepository.save(rma);
        entityManager.flush();

        saved.getLines().clear();
        returnRepository.save(saved);
        entityManager.flush();
        entityManager.clear();

        assertThat(returnLineRepository.findByReturnRequestId(saved.getId())).isEmpty();
    }

    private Return baseReturn() {
        return Return.builder()
            .rmaNumber("RMA-" + System.nanoTime())
            .orderId("order-1")
            .userId("user-1")
            .status(ReturnStatus.RECEIVED)
            .reason("defect")
            .lines(new java.util.ArrayList<>())
            .build();
    }

    private ReturnLine line(String itemId, int qty, BigDecimal unitPrice, boolean approved) {
        return ReturnLine.builder()
            .orderItemId(itemId)
            .productId("prod-" + itemId)
            .quantity(qty)
            .unitPrice(unitPrice)
            .approved(approved)
            .reason("r")
            .build();
    }

    @Test
    @DisplayName("repository_findByReturnRequestId_returnsEmpty_forUnknownId")
    void repository_findByReturnRequestId_returnsEmpty_forUnknownId() {
        assertThat(returnLineRepository.findByReturnRequestId("nope")).isEqualTo(List.of());
    }

    @Test
    @DisplayName("findByIdWithLines_should_eagerlyFetchLines_afterContextCleared")
    void findByIdWithLines_should_eagerlyFetchLines_afterContextCleared() {
        Return rma = baseReturn();
        rma.addLine(line("item-1", 2, new BigDecimal("30.00"), true));
        Return saved = returnRepository.save(rma);
        entityManager.flush();
        entityManager.clear();

        Return reloaded = returnRepository.findByIdWithLines(saved.getId()).orElseThrow();
        // Cleared context = detached entity; lines must already be initialized.
        assertThat(reloaded.getLines()).hasSize(1);
        assertThat(reloaded.approvedLinesTotal()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("findByUserIdWithLines_should_returnDistinctRowsWithLines")
    void findByUserIdWithLines_should_returnDistinctRowsWithLines() {
        Return rma = baseReturn();
        rma.addLine(line("item-1", 1, new BigDecimal("5.00"), true));
        rma.addLine(line("item-2", 2, new BigDecimal("7.50"), false));
        returnRepository.save(rma);
        entityManager.flush();
        entityManager.clear();

        List<Return> result = returnRepository.findByUserIdWithLines("user-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLines()).hasSize(2);
    }
}
