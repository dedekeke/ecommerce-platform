package com.ecommerce.orderservice.saga.refund.step;

import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.grpc.proto.inventory.InventoryServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.inventory.RestoreStockRequest;
import com.ecommerce.orderservice.grpc.proto.inventory.RestoreStockResponse;
import com.ecommerce.orderservice.grpc.proto.inventory.StockItem;
import com.ecommerce.orderservice.saga.refund.RefundSagaContext;
import com.ecommerce.orderservice.saga.refund.RefundSagaStep;
import com.ecommerce.orderservice.saga.refund.SagaStep;
import com.ecommerce.orderservice.saga.refund.StepResult;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class RestoreInventoryStep implements SagaStep {

    @GrpcClient("inventory-service")
    InventoryServiceGrpc.InventoryServiceBlockingStub inventoryServiceStub;

    @Override
    public RefundSagaStep id() {
        return RefundSagaStep.RESTORE_INVENTORY;
    }

    @Override
    public boolean hasCompensation() {
        return true;
    }

    @Override
    public StepResult execute(RefundSagaContext ctx) {
        if (ctx.getOrder() == null || ctx.getOrder().getItems() == null
            || ctx.getOrder().getItems().isEmpty()) {
            return StepResult.failure("Order has no items to restore");
        }
        try {
            String restorationId = ctx.getRestorationId() != null
                ? ctx.getRestorationId()
                : UUID.randomUUID().toString();

            RestoreStockRequest.Builder requestBuilder = RestoreStockRequest.newBuilder()
                .setRestorationId(restorationId)
                .setOrderId(ctx.getOrderId())
                .setReason(ctx.getReason() == null ? "Customer refund" : ctx.getReason());

            for (OrderItem item : ctx.getOrder().getItems()) {
                requestBuilder.addItems(StockItem.newBuilder()
                    .setProductId(item.getProductId())
                    .setQuantity(item.getQuantity())
                    .build());
            }

            RestoreStockResponse response = inventoryServiceStub.restoreStock(requestBuilder.build());
            if (!response.getSuccess()) {
                return StepResult.failure(
                    "Inventory restore failed: " + response.getMessage());
            }
            ctx.setRestorationId(restorationId);
            log.info("Inventory restored: restorationId={} order={}",
                restorationId, ctx.getOrderId());
            return StepResult.ok();
        } catch (Exception e) {
            log.error("Error calling inventory-service restoreStock", e);
            return StepResult.failure("Inventory service error: " + e.getMessage());
        }
    }

    /**
     * Compensation: in a real system we'd issue an `un-restore` (i.e. take
     * the units back out of available stock). For the saga learning
     * exercise we log a warning so the sample is auditable. Logging-only
     * compensation is realistic for inventory: an out-of-band reconciliation
     * job typically owns truth-up after a partial-saga failure.
     */
    @Override
    public void compensate(RefundSagaContext ctx) {
        log.warn("[COMPENSATE] Inventory restoration {} for order {} should be"
                + " un-restored — manual reconciliation may be required",
            ctx.getRestorationId(), ctx.getOrderId());
    }
}
