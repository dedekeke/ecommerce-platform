package com.ecommerce.inventoryservice.saga.replenishment;

import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockLowDetector — choreography trigger scanner")
class StockLowDetectorTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ReplenishmentEventPublisher publisher;

    @InjectMocks
    private StockLowDetector detector;

    private Inventory belowThreshold;
    private Inventory aboveThreshold;

    @BeforeEach
    void setUp() {
        belowThreshold = Inventory.builder()
                .id("a1")
                .productId("100")
                .sku("SKU-100")
                .quantity(2)
                .reservedQuantity(0)
                .reorderLevel(5)
                .reorderQuantity(20)
                .build();

        aboveThreshold = Inventory.builder()
                .id("a2")
                .productId("200")
                .sku("SKU-200")
                .quantity(50)
                .reservedQuantity(0)
                .reorderLevel(5)
                .reorderQuantity(20)
                .build();
    }

    @Test
    @DisplayName("should publish stock.low.detected when available qty <= threshold")
    void should_publishStockLow_when_availableBelowThreshold() {
        when(inventoryRepository.findAll()).thenReturn(List.of(belowThreshold));

        detector.scan();

        ArgumentCaptor<StockLowDetectedEvent> captor = ArgumentCaptor.forClass(StockLowDetectedEvent.class);
        verify(publisher).publishStockLowDetected(captor.capture());
        StockLowDetectedEvent event = captor.getValue();
        assertThat(event.productId()).isEqualTo(100L);
        assertThat(event.sku()).isEqualTo("SKU-100");
        assertThat(event.currentQty()).isEqualTo(2);
        assertThat(event.threshold()).isEqualTo(5);
        assertThat(event.eventId()).isNotNull();
    }

    @Test
    @DisplayName("should NOT publish when available qty is above threshold")
    void should_notPublish_when_availableAboveThreshold() {
        when(inventoryRepository.findAll()).thenReturn(List.of(aboveThreshold));

        detector.scan();

        verify(publisher, never()).publishStockLowDetected(any());
        verify(publisher, never()).publishStockReplenished(any());
    }

    @Test
    @DisplayName("should publish stock.low.detected only once for same product across multiple scans (edge-trigger)")
    void should_publishOnce_when_productRemainsLowAcrossScans() {
        when(inventoryRepository.findAll()).thenReturn(List.of(belowThreshold));

        detector.scan();
        detector.scan();
        detector.scan();

        verify(publisher, times(1)).publishStockLowDetected(any());
    }

    @Test
    @DisplayName("should publish stock.replenished when product recovers above threshold")
    void should_publishReplenished_when_productRecovers() {
        when(inventoryRepository.findAll()).thenReturn(List.of(belowThreshold));
        detector.scan();

        Inventory recovered = Inventory.builder()
                .id("a1")
                .productId("100")
                .sku("SKU-100")
                .quantity(50)
                .reservedQuantity(0)
                .reorderLevel(5)
                .reorderQuantity(20)
                .build();
        when(inventoryRepository.findAll()).thenReturn(List.of(recovered));
        when(inventoryRepository.findByProductId("100")).thenReturn(Optional.of(recovered));

        detector.scan();

        ArgumentCaptor<StockReplenishedEvent> captor = ArgumentCaptor.forClass(StockReplenishedEvent.class);
        verify(publisher).publishStockReplenished(captor.capture());
        StockReplenishedEvent event = captor.getValue();
        assertThat(event.productId()).isEqualTo(100L);
        assertThat(event.currentQty()).isEqualTo(50);
    }

    @Test
    @DisplayName("should skip rows with non-numeric productId rather than corrupt the contract")
    void should_skipRow_when_productIdNotNumeric() {
        Inventory bad = Inventory.builder()
                .id("a3")
                .productId("not-a-number")
                .sku("SKU-BAD")
                .quantity(0)
                .reservedQuantity(0)
                .reorderLevel(5)
                .reorderQuantity(20)
                .build();
        when(inventoryRepository.findAll()).thenReturn(List.of(bad));

        detector.scan();

        verify(publisher, never()).publishStockLowDetected(any());
    }
}
