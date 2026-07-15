package com.ecommerce.inventoryservice.grpc;

import com.ecommerce.common.grpc.inventory.AvailabilityResponse;
import com.ecommerce.common.grpc.inventory.BulkAvailabilityResponse;
import com.ecommerce.common.grpc.inventory.BulkCheckAvailabilityRequest;
import com.ecommerce.common.grpc.inventory.CheckAvailabilityRequest;
import com.ecommerce.common.grpc.inventory.InventoryServiceGrpc;
import com.ecommerce.common.grpc.inventory.ReservationResponse;
import com.ecommerce.common.grpc.inventory.ReserveStockRequest;
import com.ecommerce.common.grpc.inventory.StockItem;
import com.ecommerce.inventoryservice.domain.entity.Inventory;
import com.ecommerce.inventoryservice.domain.entity.InventoryReservation;
import com.ecommerce.inventoryservice.domain.enums.ReservationStatus;
import com.ecommerce.inventoryservice.mapper.InventoryGrpcMapper;
import com.ecommerce.inventoryservice.service.InventoryService;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the {@code Collectors.toMap} merge function on the gRPC boundary: a
 * single request carrying the same productId twice must sum the quantities
 * instead of throwing {@code IllegalStateException} (which previously surfaced
 * as an uncaught gRPC onError).
 */
@DisplayName("Inventory gRPC — duplicate productId within one request")
class InventoryGrpcDuplicateProductTest {

    private InventoryService inventoryService;
    private Server server;
    private ManagedChannel channel;
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        inventoryService = mock(InventoryService.class);
        InventoryGrpcMapper grpcMapper = mock(InventoryGrpcMapper.class);
        when(grpcMapper.mapReservationStatus(any()))
                .thenReturn(com.ecommerce.common.grpc.inventory.ReservationStatus.RESERVED);

        InventoryGrpcServiceImpl impl = new InventoryGrpcServiceImpl(inventoryService, grpcMapper);

        String name = InProcessServerBuilder.generateName();
        server = InProcessServerBuilder.forName(name)
                .directExecutor().addService(impl).build().start();
        channel = InProcessChannelBuilder.forName(name).directExecutor().build();
        stub = InventoryServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    @DisplayName("should_sumQuantities_when_reserveStockHasDuplicateProductId")
    void should_sumQuantities_when_reserveStockHasDuplicateProductId() {
        InventoryReservation reservation = InventoryReservation.builder()
                .id("res-1")
                .productId("p-1")
                .orderId("o-1")
                .quantity(5)
                .status(ReservationStatus.RESERVED)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        when(inventoryService.bulkReserveStock(eq("o-1"), any(), any()))
                .thenReturn(List.of(reservation));

        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setOrderId("o-1")
                .setExpirationMinutes(15)
                .addItems(StockItem.newBuilder().setProductId("p-1").setQuantity(2).build())
                .addItems(StockItem.newBuilder().setProductId("p-1").setQuantity(3).build())
                .build();

        ReservationResponse response = stub.reserveStock(request);

        assertThat(response.getSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Integer>> captor = ArgumentCaptor.forClass(Map.class);
        verify(inventoryService).bulkReserveStock(eq("o-1"), captor.capture(), any());
        assertThat(captor.getValue()).containsEntry("p-1", 5).hasSize(1);
    }

    @Test
    @DisplayName("should_sumQuantities_when_bulkCheckHasDuplicateProductId")
    void should_sumQuantities_when_bulkCheckHasDuplicateProductId() {
        when(inventoryService.bulkCheckAvailability(any())).thenReturn(Map.of("p-1", true));
        when(inventoryService.getInventoryByProductId("p-1")).thenReturn(
                Inventory.builder().productId("p-1").quantity(10).reservedQuantity(0).build());

        BulkCheckAvailabilityRequest request = BulkCheckAvailabilityRequest.newBuilder()
                .addItems(CheckAvailabilityRequest.newBuilder().setProductId("p-1").setQuantity(2).build())
                .addItems(CheckAvailabilityRequest.newBuilder().setProductId("p-1").setQuantity(3).build())
                .build();

        BulkAvailabilityResponse response = stub.bulkCheckAvailability(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getItemsList()).extracting(AvailabilityResponse::getProductId).contains("p-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Integer>> captor = ArgumentCaptor.forClass(Map.class);
        verify(inventoryService).bulkCheckAvailability(captor.capture());
        assertThat(captor.getValue()).containsEntry("p-1", 5).hasSize(1);
    }
}
