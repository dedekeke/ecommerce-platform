package com.ecommerce.orderservice.saga;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.event.OrderEventPublisher;
import com.ecommerce.orderservice.grpc.proto.cart.CartItem;
import com.ecommerce.orderservice.grpc.proto.cart.CartServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.cart.ClearCartRequest;
import com.ecommerce.orderservice.grpc.proto.cart.GetCartRequest;
import com.ecommerce.orderservice.grpc.proto.cart.GetCartResponse;
import com.ecommerce.orderservice.grpc.proto.inventory.InventoryServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.inventory.ReserveStockRequest;
import com.ecommerce.orderservice.grpc.proto.inventory.ReserveStockResponse;
import com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationRequest;
import com.ecommerce.orderservice.grpc.proto.inventory.StockItem;
import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentRequest;
import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentResponse;
import com.ecommerce.orderservice.grpc.proto.payment.PaymentServiceGrpc;
import com.ecommerce.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Saga orchestrator for order creation flow
 * Implements compensation logic for rollback on failure
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreationSaga {

    private final OrderService orderService;
    private final OrderEventPublisher eventPublisher;

    @GrpcClient("cart-service")
    private CartServiceGrpc.CartServiceBlockingStub cartServiceStub;

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryServiceStub;

    @GrpcClient("payment-service")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    /**
     * Execute the order creation saga
     * Steps:
     * 1. Get cart items from Cart Service (gRPC)
     * 2. Reserve stock in Inventory Service (gRPC)
     * 3. Create payment intent in Payment Service (gRPC)
     * 4. Create order in database
     * 5. Clear cart
     * 6. Publish OrderCreatedEvent to Kafka
     *
     * If any step fails, compensate previous steps
     */
    @Transactional
    public Order executeOrderCreationSaga(String userId, Address shippingAddress, String promotionCode) {
        log.info("Starting order creation saga for user: {}", userId);

        String reservationId = null;
        String paymentIntentId = null;
        Order order = null;

        try {
            // Step 1: Get cart items
            log.info("Saga Step 1: Getting cart items for user: {}", userId);
            GetCartResponse cartResponse = getCartItems(userId);
            if (!cartResponse.getSuccess() || cartResponse.getItemsList().isEmpty()) {
                throw new SagaException("Cart is empty or could not be retrieved");
            }

            List<OrderItem> orderItems = convertCartItemsToOrderItems(cartResponse.getItemsList());

            // Step 2: Reserve stock
            log.info("Saga Step 2: Reserving stock for {} items", orderItems.size());
            ReserveStockResponse reserveResponse = reserveStock(orderItems);
            if (!reserveResponse.getSuccess()) {
                throw new SagaException("Failed to reserve stock: " + reserveResponse.getMessage());
            }
            reservationId = reserveResponse.getReservationId();
            log.info("Stock reserved successfully. Reservation ID: {}", reservationId);

            // Step 3: Create order (to get order ID for payment)
            log.info("Saga Step 3: Creating order in database");
            order = orderService.createOrder(userId, orderItems, shippingAddress, promotionCode);
            log.info("Order created: {}", order.getOrderNumber());

            // Step 4: Create payment intent
            log.info("Saga Step 4: Creating payment intent for order: {}", order.getId());
            CreatePaymentIntentResponse paymentResponse = createPaymentIntent(
                order.getId(),
                userId,
                order.getTotal()
            );
            if (!paymentResponse.getSuccess()) {
                throw new SagaException("Failed to create payment intent: " + paymentResponse.getMessage());
            }
            paymentIntentId = paymentResponse.getPaymentIntentId();
            log.info("Payment intent created: {}", paymentIntentId);

            // Update order with payment intent ID
            orderService.setPaymentIntent(order.getId(), paymentIntentId);

            // Step 5: Clear cart
            log.info("Saga Step 5: Clearing cart for user: {}", userId);
            clearCart(userId);

            // Step 6: Publish event
            log.info("Saga Step 6: Publishing order created event");
            eventPublisher.publishOrderCreatedEvent(order);

            log.info("Order creation saga completed successfully for order: {}", order.getOrderNumber());
            return order;

        } catch (Exception e) {
            log.error("Order creation saga failed. Starting compensation...", e);

            // Compensate in reverse order
            compensate(order, reservationId, paymentIntentId, userId);

            throw new SagaException("Order creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Compensate saga steps in reverse order
     */
    private void compensate(Order order, String reservationId, String paymentIntentId, String userId) {
        log.warn("Compensating order creation saga for user: {}", userId);

        // Compensation Step 1: Release stock reservation
        if (reservationId != null) {
            try {
                log.info("Compensation: Releasing stock reservation: {}", reservationId);
                releaseStockReservation(reservationId, order != null ? order.getId() : null);
            } catch (Exception e) {
                log.error("Failed to release stock reservation during compensation", e);
            }
        }

        // Compensation Step 2: Cancel order if created
        if (order != null) {
            try {
                log.info("Compensation: Cancelling order: {}", order.getId());
                orderService.updateOrderStatus(order.getId(), OrderStatus.CANCELLED);
                eventPublisher.publishOrderCancelledEvent(order);
            } catch (Exception e) {
                log.error("Failed to cancel order during compensation", e);
            }
        }

        // Note: We don't need to compensate payment intent as it's not charged yet
        // Note: We don't clear cart on failure so user can retry

        log.info("Compensation completed");
    }

    // gRPC Client Methods

    private GetCartResponse getCartItems(String userId) {
        try {
            GetCartRequest request = GetCartRequest.newBuilder()
                .setUserId(userId)
                .build();
            return cartServiceStub.getCart(request);
        } catch (Exception e) {
            log.error("Error calling Cart Service", e);
            throw new SagaException("Failed to get cart items: " + e.getMessage(), e);
        }
    }

    private ReserveStockResponse reserveStock(List<OrderItem> items) {
        try {
            // For now, use a temporary order ID (will be replaced with actual order ID)
            String tempOrderId = "temp-" + System.currentTimeMillis();

            ReserveStockRequest.Builder requestBuilder = ReserveStockRequest.newBuilder()
                .setOrderId(tempOrderId);

            items.forEach(item -> {
                StockItem stockItem = StockItem.newBuilder()
                    .setProductId(item.getProductId())
                    .setQuantity(item.getQuantity())
                    .build();
                requestBuilder.addItems(stockItem);
            });

            return inventoryServiceStub.reserveStock(requestBuilder.build());
        } catch (Exception e) {
            log.error("Error calling Inventory Service", e);
            throw new SagaException("Failed to reserve stock: " + e.getMessage(), e);
        }
    }

    private void releaseStockReservation(String reservationId, String orderId) {
        try {
            ReleaseReservationRequest request = ReleaseReservationRequest.newBuilder()
                .setReservationId(reservationId)
                .setOrderId(orderId != null ? orderId : "")
                .setReason("Order creation failed - compensation")
                .build();
            inventoryServiceStub.releaseReservation(request);
        } catch (Exception e) {
            log.error("Error calling Inventory Service for compensation", e);
            throw new SagaException("Failed to release stock reservation: " + e.getMessage(), e);
        }
    }

    private CreatePaymentIntentResponse createPaymentIntent(String orderId, String userId, BigDecimal amount) {
        try {
            CreatePaymentIntentRequest request = CreatePaymentIntentRequest.newBuilder()
                .setOrderId(orderId)
                .setUserId(userId)
                .setAmount(amount.doubleValue())
                .setCurrency("USD")
                .build();
            return paymentServiceStub.createPaymentIntent(request);
        } catch (Exception e) {
            log.error("Error calling Payment Service", e);
            throw new SagaException("Failed to create payment intent: " + e.getMessage(), e);
        }
    }

    private void clearCart(String userId) {
        try {
            ClearCartRequest request = ClearCartRequest.newBuilder()
                .setUserId(userId)
                .build();
            cartServiceStub.clearCart(request);
        } catch (Exception e) {
            log.error("Error clearing cart", e);
            // Don't throw - this is not critical for order creation
        }
    }

    // Helper Methods

    private List<OrderItem> convertCartItemsToOrderItems(List<CartItem> cartItems) {
        return cartItems.stream()
            .map(cartItem -> OrderItem.builder()
                .productId(cartItem.getProductId())
                .productName(cartItem.getProductName())
                .price(BigDecimal.valueOf(cartItem.getPrice()))
                .quantity(cartItem.getQuantity())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Custom exception for saga failures
     */
    public static class SagaException extends RuntimeException {
        public SagaException(String message) {
            super(message);
        }

        public SagaException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
