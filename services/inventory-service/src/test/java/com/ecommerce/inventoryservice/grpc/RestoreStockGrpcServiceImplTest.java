package com.ecommerce.inventoryservice.grpc;

import com.ecommerce.common.grpc.inventory.InventoryServiceGrpc;
import com.ecommerce.common.grpc.inventory.RestoreStockLineItem;
import com.ecommerce.common.grpc.inventory.RestoreStockRequest;
import com.ecommerce.common.grpc.inventory.RestoreStockResponse;
import com.ecommerce.inventoryservice.exception.InventoryNotFoundException;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("RestoreStock gRPC server impl — refund saga endpoint")
class RestoreStockGrpcServiceImplTest {

    private InventoryService inventoryService;
    private InventoryGrpcMapper grpcMapper;
    private Server server;
    private ManagedChannel channel;
    private InventoryServiceGrpc.InventoryServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        inventoryService = mock(InventoryService.class);
        grpcMapper = mock(InventoryGrpcMapper.class);
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
    @DisplayName("should_restoreUnits_when_restorationIdIsNew")
    void should_restoreUnits_when_restorationIdIsNew() {
        when(inventoryService.restoreStock(eq("r-1"), eq("o-1"), anyString(), any()))
                .thenReturn(true);

        RestoreStockRequest request = RestoreStockRequest.newBuilder()
                .setRestorationId("r-1")
                .setOrderId("o-1")
                .setReason("Customer refund")
                .addItems(RestoreStockLineItem.newBuilder().setProductId("p-1").setQuantity(2).build())
                .addItems(RestoreStockLineItem.newBuilder().setProductId("p-2").setQuantity(3).build())
                .build();

        RestoreStockResponse response = stub.restoreStock(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getRestorationId()).isEqualTo("r-1");
        assertThat(response.getMessage()).contains("restored");

        verify(inventoryService).restoreStock(
                eq("r-1"),
                eq("o-1"),
                eq("Customer refund"),
                eq(Map.of("p-1", 2, "p-2", 3))
        );
    }

    @Test
    @DisplayName("should_returnSuccess_butSkip_when_restorationIdAlreadySeen")
    void should_returnSuccess_butSkip_when_restorationIdAlreadySeen() {
        when(inventoryService.restoreStock(eq("r-dup"), anyString(), anyString(), any()))
                .thenReturn(false);

        RestoreStockRequest request = RestoreStockRequest.newBuilder()
                .setRestorationId("r-dup")
                .setOrderId("o-1")
                .setReason("Customer refund")
                .addItems(RestoreStockLineItem.newBuilder().setProductId("p-1").setQuantity(2).build())
                .build();

        RestoreStockResponse response = stub.restoreStock(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getMessage()).containsIgnoringCase("idempotent");
    }

    @Test
    @DisplayName("should_returnFailure_when_restorationIdIsBlank")
    void should_returnFailure_when_restorationIdIsBlank() {
        RestoreStockRequest request = RestoreStockRequest.newBuilder()
                .setRestorationId("")
                .setOrderId("o-1")
                .addItems(RestoreStockLineItem.newBuilder().setProductId("p-1").setQuantity(1).build())
                .build();

        RestoreStockResponse response = stub.restoreStock(request);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).containsIgnoringCase("restoration_id");
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("should_returnFailure_when_itemsListIsEmpty")
    void should_returnFailure_when_itemsListIsEmpty() {
        RestoreStockRequest request = RestoreStockRequest.newBuilder()
                .setRestorationId("r-2")
                .setOrderId("o-2")
                .build();

        RestoreStockResponse response = stub.restoreStock(request);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).containsIgnoringCase("items");
    }

    @Test
    @DisplayName("should_returnFailureWithMessage_when_productNotFound")
    void should_returnFailureWithMessage_when_productNotFound() {
        when(inventoryService.restoreStock(anyString(), anyString(), anyString(), any()))
                .thenThrow(new InventoryNotFoundException("Inventory not found for product: p-9"));

        RestoreStockRequest request = RestoreStockRequest.newBuilder()
                .setRestorationId("r-3")
                .setOrderId("o-3")
                .addItems(RestoreStockLineItem.newBuilder().setProductId("p-9").setQuantity(1).build())
                .build();

        RestoreStockResponse response = stub.restoreStock(request);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).contains("p-9");
    }
}
