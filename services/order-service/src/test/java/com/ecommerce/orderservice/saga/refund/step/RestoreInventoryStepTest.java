package com.ecommerce.orderservice.saga.refund.step;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.grpc.proto.inventory.InventoryServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.inventory.RestoreStockRequest;
import com.ecommerce.orderservice.grpc.proto.inventory.RestoreStockResponse;
import com.ecommerce.orderservice.saga.refund.RefundSagaContext;
import com.ecommerce.orderservice.saga.refund.StepResult;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestoreInventoryStepTest {

    private final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
    private MockInventoryService mockInventory;
    private ManagedChannel channel;
    private RestoreInventoryStep step;

    @BeforeEach
    void setUp() throws Exception {
        mockInventory = new MockInventoryService();
        String name = InProcessServerBuilder.generateName();
        grpcCleanup.register(InProcessServerBuilder.forName(name)
            .directExecutor().addService(mockInventory).build().start());
        channel = grpcCleanup.register(
            InProcessChannelBuilder.forName(name).directExecutor().build());

        step = new RestoreInventoryStep();
        step.inventoryServiceStub = InventoryServiceGrpc.newBlockingStub(channel);
    }

    @Test
    void should_restoreEachItem_andRecordRestorationId() {
        mockInventory.success = true;

        RefundSagaContext ctx = ctxWithItems();
        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isTrue();
        assertThat(ctx.getRestorationId()).isNotNull();
        assertThat(mockInventory.lastRequest.getItemsCount()).isEqualTo(2);
        assertThat(mockInventory.lastRequest.getRestorationId()).isEqualTo(ctx.getRestorationId());
    }

    @Test
    void should_reuseRestorationId_whenAlreadySet() {
        mockInventory.success = true;
        RefundSagaContext ctx = ctxWithItems();
        ctx.setRestorationId("preset-id");

        step.execute(ctx);

        assertThat(ctx.getRestorationId()).isEqualTo("preset-id");
        assertThat(mockInventory.lastRequest.getRestorationId()).isEqualTo("preset-id");
    }

    @Test
    void should_fail_whenInventoryServiceFails() {
        mockInventory.success = false;
        mockInventory.message = "stock locked";

        StepResult result = step.execute(ctxWithItems());

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("stock locked");
    }

    @Test
    void should_fail_whenOrderHasNoItems() {
        Order order = new Order();
        order.setId("o1");
        order.setItems(new ArrayList<>());
        RefundSagaContext ctx = RefundSagaContext.builder()
            .orderId("o1").order(order).build();

        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("no items");
    }

    @Test
    void compensate_isLogOnly_doesNotThrow() {
        step.compensate(RefundSagaContext.builder().build());
    }

    @Test
    void execute_should_returnFailure_whenStubThrows() {
        RestoreInventoryStep broken = new RestoreInventoryStep();
        broken.inventoryServiceStub = null;

        StepResult result = broken.execute(ctxWithItems());

        assertThat(result.successful()).isFalse();
        assertThat(result.message()).contains("Inventory service error");
    }

    @Test
    void execute_should_fail_whenOrderIsNull() {
        RefundSagaContext ctx = RefundSagaContext.builder().orderId("o1").build();

        StepResult result = step.execute(ctx);

        assertThat(result.successful()).isFalse();
    }

    private RefundSagaContext ctxWithItems() {
        Order order = new Order();
        order.setId("o1");
        List<OrderItem> items = new ArrayList<>();
        items.add(OrderItem.builder().productId("p1").quantity(2)
            .price(new BigDecimal("10")).productName("A").build());
        items.add(OrderItem.builder().productId("p2").quantity(1)
            .price(new BigDecimal("5")).productName("B").build());
        order.setItems(items);
        return RefundSagaContext.builder().orderId("o1").order(order).build();
    }

    private static class MockInventoryService extends InventoryServiceGrpc.InventoryServiceImplBase {
        boolean success = true;
        String message = "ok";
        RestoreStockRequest lastRequest;

        @Override
        public void restoreStock(RestoreStockRequest request,
            StreamObserver<RestoreStockResponse> responseObserver) {
            lastRequest = request;
            responseObserver.onNext(RestoreStockResponse.newBuilder()
                .setSuccess(success).setMessage(message)
                .setRestorationId(request.getRestorationId()).build());
            responseObserver.onCompleted();
        }
    }
}
