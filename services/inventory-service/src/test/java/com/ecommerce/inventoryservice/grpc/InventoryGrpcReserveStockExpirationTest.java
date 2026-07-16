package com.ecommerce.inventoryservice.grpc;

import com.ecommerce.common.grpc.inventory.ReservationResponse;
import com.ecommerce.common.grpc.inventory.ReserveStockRequest;
import com.ecommerce.common.grpc.inventory.StockItem;
import com.ecommerce.common.grpc.inventory.InventoryServiceGrpc;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the {@code expiration_minutes} handling on the ReserveStock gRPC
 * boundary. proto3 defaults an unset int32 to 0, so a request without the field
 * (or with a negative value) must NOT unbox a null Integer into a primitive int
 * — that previously threw NPE. Instead the service receives {@code null} and
 * applies its configured default TTL.
 */
@DisplayName("Inventory gRPC — ReserveStock expiration_minutes handling")
class InventoryGrpcReserveStockExpirationTest {

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

        InventoryReservation reservation = InventoryReservation.builder()
                .id("res-1")
                .productId("p-1")
                .orderId("o-1")
                .quantity(2)
                .status(ReservationStatus.RESERVED)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();
        when(inventoryService.bulkReserveStock(eq("o-1"), any(), any()))
                .thenReturn(List.of(reservation));

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

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -30})
    @DisplayName("should_notThrowAndPassNullTtl_when_expirationMinutesIsNonPositive")
    void should_notThrowAndPassNullTtl_when_expirationMinutesIsNonPositive(int expiration) {
        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setOrderId("o-1")
                .setExpirationMinutes(expiration)
                .addItems(StockItem.newBuilder().setProductId("p-1").setQuantity(2).build())
                .build();

        ReservationResponse response = stub.reserveStock(request);

        assertThat(response.getSuccess()).isTrue();

        ArgumentCaptor<Integer> ttlCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(inventoryService).bulkReserveStock(eq("o-1"), any(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isNull();
    }

    @Test
    @DisplayName("should_passThroughTtl_when_expirationMinutesIsPositive")
    void should_passThroughTtl_when_expirationMinutesIsPositive() {
        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setOrderId("o-1")
                .setExpirationMinutes(30)
                .addItems(StockItem.newBuilder().setProductId("p-1").setQuantity(2).build())
                .build();

        ReservationResponse response = stub.reserveStock(request);

        assertThat(response.getSuccess()).isTrue();

        ArgumentCaptor<Integer> ttlCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(inventoryService).bulkReserveStock(eq("o-1"), any(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isEqualTo(30);
    }

    @Test
    @DisplayName("should_notThrow_when_expirationMinutesFieldIsUnset")
    void should_notThrow_when_expirationMinutesFieldIsUnset() {
        ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setOrderId("o-1")
                .addItems(StockItem.newBuilder().setProductId("p-1").setQuantity(2).build())
                .build();

        ReservationResponse response = stub.reserveStock(request);

        assertThat(response.getSuccess()).isTrue();

        ArgumentCaptor<Integer> ttlCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(inventoryService).bulkReserveStock(eq("o-1"), any(), ttlCaptor.capture());
        assertThat(ttlCaptor.getValue()).isNull();
    }
}
