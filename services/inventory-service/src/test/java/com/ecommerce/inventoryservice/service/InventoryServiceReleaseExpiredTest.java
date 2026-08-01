package com.ecommerce.inventoryservice.service;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.InventoryReservation;
import com.ecommerce.inventoryservice.domain.enums.InventoryStatus;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import com.ecommerce.inventoryservice.repository.InventoryReservationRepository;
import com.ecommerce.inventoryservice.repository.InventoryRestorationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the paged {@code releaseExpiredReservations} loop that replaced the
 * unbounded fetch: it must iterate in fixed-size batches and never spin forever
 * when a backlog row cannot be released.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("InventoryService — release expired reservations (paged)")
class InventoryServiceReleaseExpiredTest {

    private static final int BATCH_SIZE = 2;

    @Mock private InventoryRepository inventoryRepository;
    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private InventoryRestorationRepository restorationRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, reservationRepository,
                restorationRepository, kafkaTemplate, applicationEventPublisher, transactionManager);
        ReflectionTestUtils.setField(service, "expiredReleaseBatchSize", BATCH_SIZE);
    }

    @Test
    @DisplayName("should_pageInBatches_when_backlogSpansMultiplePages")
    void should_pageInBatches_when_backlogSpansMultiplePages() {
        when(inventoryRepository.findByProductIdForUpdate(anyString()))
                .thenAnswer(inv -> Optional.of(inventoryFor(inv.getArgument(0))));
        // Full batch, full batch, then a partial page signals the drain.
        when(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
                .thenReturn(batch(2), batch(2), batch(1));

        int released = service.releaseExpiredReservations();

        assertThat(released).isEqualTo(5);

        ArgumentCaptor<Pageable> pageCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reservationRepository, times(3))
                .findExpiredReservations(any(), pageCaptor.capture());
        assertThat(pageCaptor.getAllValues())
                .allSatisfy(p -> assertThat(p.getPageSize()).isEqualTo(BATCH_SIZE));
    }

    @Test
    @DisplayName("should_notLoopForever_when_everyRowFailsToRelease")
    void should_notLoopForever_when_everyRowFailsToRelease() {
        // Missing inventory means every release attempt fails, so rows keep
        // matching the query. Without the no-progress guard this would spin.
        when(inventoryRepository.findByProductIdForUpdate(anyString()))
                .thenReturn(Optional.empty());
        when(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
                .thenReturn(batch(2));

        int released = service.releaseExpiredReservations();

        assertThat(released).isZero();
        verify(reservationRepository, times(1))
                .findExpiredReservations(any(), any(Pageable.class));
    }

    @Test
    @DisplayName("should_releaseHealthyRowsBehindPoisonRow_when_headRowPersistentlyFails")
    void should_releaseHealthyRowsBehindPoisonRow_when_headRowPersistentlyFails() {
        // The poison row (earliest expiresAt, so it sorts to the head) can never
        // be released because its inventory is missing. Healthy rows ordered
        // behind it must still be released instead of being head-of-line blocked.
        InventoryReservation poison = reservation("res-poison", "p-bad");
        InventoryReservation ok1 = reservation("res-ok-1", "p-ok");
        InventoryReservation ok2 = reservation("res-ok-2", "p-ok");
        InventoryReservation ok3 = reservation("res-ok-3", "p-ok");

        when(inventoryRepository.findByProductIdForUpdate("p-bad")).thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate("p-ok"))
                .thenAnswer(inv -> Optional.of(inventoryFor("p-ok")));

        // First fetch: [poison, ok1]. poison stays RESERVED (fails) and is then
        // excluded, so the second fetch surfaces the previously-blocked rows.
        when(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
                .thenReturn(List.of(poison, ok1));
        when(reservationRepository.findExpiredReservationsExcluding(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(ok2, ok3), List.of());

        int released = service.releaseExpiredReservations();

        assertThat(released).isEqualTo(3);
    }

    @Test
    @DisplayName("should_excludeFailedRowsFromSubsequentFetch_when_rowFails")
    void should_excludeFailedRowsFromSubsequentFetch_when_rowFails() {
        InventoryReservation poison = reservation("res-poison", "p-bad");
        InventoryReservation ok1 = reservation("res-ok-1", "p-ok");

        when(inventoryRepository.findByProductIdForUpdate("p-bad")).thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdForUpdate("p-ok"))
                .thenAnswer(inv -> Optional.of(inventoryFor("p-ok")));
        when(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
                .thenReturn(List.of(poison, ok1));
        when(reservationRepository.findExpiredReservationsExcluding(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.releaseExpiredReservations();

        ArgumentCaptor<Collection<String>> excludedCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(reservationRepository, atLeastOnce())
                .findExpiredReservationsExcluding(any(), excludedCaptor.capture(), any(Pageable.class));
        assertThat(excludedCaptor.getValue()).contains("res-poison").doesNotContain("res-ok-1");
    }

    @Test
    @DisplayName("should_terminate_when_everyRowInEveryPageFails")
    void should_terminate_when_everyRowInEveryPageFails() {
        // A full page of poison rows must not spin forever: failed rows are
        // excluded, so the second (excluding) fetch drains to empty.
        when(inventoryRepository.findByProductIdForUpdate(anyString())).thenReturn(Optional.empty());
        when(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
                .thenReturn(List.of(reservation("res-a", "p-a"), reservation("res-b", "p-b")));
        when(reservationRepository.findExpiredReservationsExcluding(any(), any(), any(Pageable.class)))
                .thenReturn(List.of());

        int released = service.releaseExpiredReservations();

        assertThat(released).isZero();
        verify(reservationRepository, times(1)).findExpiredReservations(any(), any(Pageable.class));
        verify(reservationRepository, times(1))
                .findExpiredReservationsExcluding(any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("should_notUseExclusionQuery_when_noRowFails")
    void should_notUseExclusionQuery_when_noRowFails() {
        when(inventoryRepository.findByProductIdForUpdate(anyString()))
                .thenAnswer(inv -> Optional.of(inventoryFor(inv.getArgument(0))));
        when(reservationRepository.findExpiredReservations(any(), any(Pageable.class)))
                .thenReturn(batch(1));

        service.releaseExpiredReservations();

        verify(reservationRepository, never())
                .findExpiredReservationsExcluding(any(), any(), any(Pageable.class));
    }

    private InventoryReservation reservation(String id, String productId) {
        return InventoryReservation.builder()
                .id(id)
                .productId(productId)
                .orderId("o-1")
                .quantity(1)
                .status(ReservationStatus.RESERVED)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
    }

    private Inventory inventoryFor(String productId) {
        return Inventory.builder()
                .productId(productId)
                .quantity(100)
                .reservedQuantity(100)
                .reorderLevel(0)
                .status(InventoryStatus.IN_STOCK)
                .build();
    }

    private List<InventoryReservation> batch(int size) {
        List<InventoryReservation> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(InventoryReservation.builder()
                    .id("res-" + System.nanoTime() + "-" + i)
                    .productId("p-" + i)
                    .orderId("o-1")
                    .quantity(1)
                    .status(ReservationStatus.RESERVED)
                    .expiresAt(LocalDateTime.now().minusMinutes(1))
                    .build());
        }
        return list;
    }
}
