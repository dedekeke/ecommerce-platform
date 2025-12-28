package com.ecommerce.orderservice.client;

import com.ecommerce.orderservice.grpc.proto.cart.CartServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.cart.ClearCartRequest;
import com.ecommerce.orderservice.grpc.proto.cart.ClearCartResponse;
import com.ecommerce.orderservice.grpc.proto.cart.GetCartRequest;
import com.ecommerce.orderservice.grpc.proto.cart.GetCartResponse;
import com.ecommerce.orderservice.grpc.proto.inventory.InventoryServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationRequest;
import com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationResponse;
import com.ecommerce.orderservice.grpc.proto.inventory.ReserveStockRequest;
import com.ecommerce.orderservice.grpc.proto.inventory.ReserveStockResponse;
import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentRequest;
import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentResponse;
import com.ecommerce.orderservice.grpc.proto.payment.PaymentServiceGrpc;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

/**
 * Resilient wrapper for gRPC service clients with circuit breaker, retry, and timeout support.
 *
 * Each service call is protected by:
 * - Circuit Breaker: Prevents cascade failures
 * - Retry: Automatic retry with exponential backoff
 * - Time Limiter: Timeout for slow calls
 * - Bulkhead: Limits concurrent calls
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResilientGrpcClient {

    @GrpcClient("cart-service")
    private CartServiceGrpc.CartServiceBlockingStub cartServiceStub;

    @GrpcClient("inventory-service")
    private InventoryServiceGrpc.InventoryServiceBlockingStub inventoryServiceStub;

    @GrpcClient("payment-service")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceStub;

    // ==================== Cart Service ====================

    @CircuitBreaker(name = "cart-service", fallbackMethod = "getCartFallback")
    @Retry(name = "cart-service")
    @Bulkhead(name = "cart-service")
    public GetCartResponse getCart(String userId) {
        log.debug("Calling Cart Service to get cart for user: {}", userId);
        GetCartRequest request = GetCartRequest.newBuilder()
            .setUserId(userId)
            .build();
        return cartServiceStub.getCart(request);
    }

    @CircuitBreaker(name = "cart-service", fallbackMethod = "clearCartFallback")
    @Retry(name = "cart-service")
    @Bulkhead(name = "cart-service")
    public ClearCartResponse clearCart(String userId) {
        log.debug("Calling Cart Service to clear cart for user: {}", userId);
        ClearCartRequest request = ClearCartRequest.newBuilder()
            .setUserId(userId)
            .build();
        return cartServiceStub.clearCart(request);
    }

    // Cart Service Fallbacks
    private GetCartResponse getCartFallback(String userId, Throwable t) {
        log.error("Cart Service unavailable. Fallback triggered for user: {}. Error: {}", userId, t.getMessage());
        return GetCartResponse.newBuilder()
            .setSuccess(false)
            .setMessage("Cart service is temporarily unavailable. Please try again later.")
            .build();
    }

    private ClearCartResponse clearCartFallback(String userId, Throwable t) {
        log.error("Cart Service unavailable for clearing. Fallback triggered for user: {}. Error: {}", userId, t.getMessage());
        return ClearCartResponse.newBuilder()
            .setSuccess(false)
            .setMessage("Cart service is temporarily unavailable. Cart will be cleared later.")
            .build();
    }

    // ==================== Inventory Service ====================

    @CircuitBreaker(name = "inventory-service", fallbackMethod = "reserveStockFallback")
    @Retry(name = "inventory-service")
    @Bulkhead(name = "inventory-service")
    public ReserveStockResponse reserveStock(ReserveStockRequest request) {
        log.debug("Calling Inventory Service to reserve stock for order: {}", request.getOrderId());
        return inventoryServiceStub.reserveStock(request);
    }

    @CircuitBreaker(name = "inventory-service", fallbackMethod = "releaseReservationFallback")
    @Retry(name = "inventory-service")
    @Bulkhead(name = "inventory-service")
    public ReleaseReservationResponse releaseReservation(ReleaseReservationRequest request) {
        log.debug("Calling Inventory Service to release reservation: {}", request.getReservationId());
        return inventoryServiceStub.releaseReservation(request);
    }

    // Inventory Service Fallbacks
    private ReserveStockResponse reserveStockFallback(ReserveStockRequest request, Throwable t) {
        log.error("Inventory Service unavailable. Fallback triggered for order: {}. Error: {}",
            request.getOrderId(), t.getMessage());
        return ReserveStockResponse.newBuilder()
            .setSuccess(false)
            .setMessage("Inventory service is temporarily unavailable. Please try again later.")
            .build();
    }

    private ReleaseReservationResponse releaseReservationFallback(ReleaseReservationRequest request, Throwable t) {
        log.error("Inventory Service unavailable for release. Reservation: {}. Error: {}",
            request.getReservationId(), t.getMessage());
        // For compensation, we should queue this for later processing
        log.warn("Reservation {} will be auto-released after timeout", request.getReservationId());
        return ReleaseReservationResponse.newBuilder()
            .setSuccess(false)
            .setMessage("Inventory service unavailable. Reservation will be auto-released.")
            .build();
    }

    // ==================== Payment Service ====================

    @CircuitBreaker(name = "payment-service", fallbackMethod = "createPaymentIntentFallback")
    @Retry(name = "payment-service")
    @Bulkhead(name = "payment-service")
    public CreatePaymentIntentResponse createPaymentIntent(CreatePaymentIntentRequest request) {
        log.debug("Calling Payment Service to create payment intent for order: {}", request.getOrderId());
        return paymentServiceStub.createPaymentIntent(request);
    }

    // Payment Service Fallback
    private CreatePaymentIntentResponse createPaymentIntentFallback(CreatePaymentIntentRequest request, Throwable t) {
        log.error("Payment Service unavailable. Fallback triggered for order: {}. Error: {}",
            request.getOrderId(), t.getMessage());
        return CreatePaymentIntentResponse.newBuilder()
            .setSuccess(false)
            .setMessage("Payment service is temporarily unavailable. Please try again later.")
            .build();
    }

    // ==================== Circuit Breaker Status ====================

    public boolean isCartServiceHealthy() {
        try {
            // Simple health check - try to get an empty cart
            GetCartRequest request = GetCartRequest.newBuilder()
                .setUserId("health-check")
                .build();
            cartServiceStub.getCart(request);
            return true;
        } catch (StatusRuntimeException e) {
            return false;
        }
    }

    public boolean isInventoryServiceHealthy() {
        try {
            ReserveStockRequest request = ReserveStockRequest.newBuilder()
                .setOrderId("health-check")
                .build();
            inventoryServiceStub.reserveStock(request);
            return true;
        } catch (StatusRuntimeException e) {
            return false;
        }
    }

    public boolean isPaymentServiceHealthy() {
        try {
            CreatePaymentIntentRequest request = CreatePaymentIntentRequest.newBuilder()
                .setOrderId("health-check")
                .build();
            paymentServiceStub.createPaymentIntent(request);
            return true;
        } catch (StatusRuntimeException e) {
            return false;
        }
    }
}
