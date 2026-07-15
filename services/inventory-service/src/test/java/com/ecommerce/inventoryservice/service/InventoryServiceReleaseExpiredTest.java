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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, reservationRepository,
                restorationRepository, kafkaTemplate, applicationEventPublisher);
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
