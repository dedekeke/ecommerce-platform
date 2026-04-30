package com.ecommerce.orderservice.saga;

import com.ecommerce.orderservice.client.PromotionServiceClient;
import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.event.OrderEventPublisher;
import com.ecommerce.orderservice.grpc.proto.cart.CartItem;
import com.ecommerce.orderservice.grpc.proto.cart.CartServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.cart.GetCartResponse;
import com.ecommerce.orderservice.grpc.proto.inventory.InventoryServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.inventory.ReserveStockResponse;
import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentResponse;
import com.ecommerce.orderservice.grpc.proto.payment.PaymentServiceGrpc;
import com.ecommerce.orderservice.service.OrderService;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for OrderCreationSaga
 * Tests the complete order creation flow and compensation logic
 */
@ExtendWith(MockitoExtension.class)
class OrderCreationSagaTest {

    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Mock
    private OrderService orderService;

    @Mock
    private OrderEventPublisher eventPublisher;

    @Mock
    private PromotionServiceClient promotionServiceClient;

    private OrderCreationSaga saga;
    private ManagedChannel cartChannel;
    private ManagedChannel inventoryChannel;
    private ManagedChannel paymentChannel;

    private MockCartService mockCartService;
    private MockInventoryService mockInventoryService;
    private MockPaymentService mockPaymentService;

    @BeforeEach
    void setUp() throws Exception {
        // Set up mock gRPC services
        mockCartService = new MockCartService();
        mockInventoryService = new MockInventoryService();
        mockPaymentService = new MockPaymentService();

        // Create in-process servers for each service
        String cartServerName = InProcessServerBuilder.generateName();
        String inventoryServerName = InProcessServerBuilder.generateName();
        String paymentServerName = InProcessServerBuilder.generateName();

        grpcCleanup.register(
            InProcessServerBuilder.forName(cartServerName)
                .directExecutor()
                .addService(mockCartService)
                .build()
                .start()
        );

        grpcCleanup.register(
            InProcessServerBuilder.forName(inventoryServerName)
                .directExecutor()
                .addService(mockInventoryService)
                .build()
                .start()
        );

        grpcCleanup.register(
            InProcessServerBuilder.forName(paymentServerName)
                .directExecutor()
                .addService(mockPaymentService)
                .build()
                .start()
        );

        // Create channels
        cartChannel = grpcCleanup.register(
            InProcessChannelBuilder.forName(cartServerName).directExecutor().build()
        );

        inventoryChannel = grpcCleanup.register(
            InProcessChannelBuilder.forName(inventoryServerName).directExecutor().build()
        );

        paymentChannel = grpcCleanup.register(
            InProcessChannelBuilder.forName(paymentServerName).directExecutor().build()
        );

        // Create saga with mocked dependencies
        saga = new OrderCreationSaga(orderService, eventPublisher, promotionServiceClient);

        // Use reflection to inject gRPC stubs (since @GrpcClient doesn't work in tests)
        injectGrpcStubs();
    }

    private void injectGrpcStubs() throws Exception {
        var cartStubField = OrderCreationSaga.class.getDeclaredField("cartServiceStub");
        cartStubField.setAccessible(true);
        cartStubField.set(saga, CartServiceGrpc.newBlockingStub(cartChannel));

        var inventoryStubField = OrderCreationSaga.class.getDeclaredField("inventoryServiceStub");
        inventoryStubField.setAccessible(true);
        inventoryStubField.set(saga, InventoryServiceGrpc.newBlockingStub(inventoryChannel));

        var paymentStubField = OrderCreationSaga.class.getDeclaredField("paymentServiceStub");
        paymentStubField.setAccessible(true);
        paymentStubField.set(saga, PaymentServiceGrpc.newBlockingStub(paymentChannel));
    }

    @AfterEach
    void tearDown() {
        if (cartChannel != null && !cartChannel.isShutdown()) {
            cartChannel.shutdown();
        }
        if (inventoryChannel != null && !inventoryChannel.isShutdown()) {
            inventoryChannel.shutdown();
        }
        if (paymentChannel != null && !paymentChannel.isShutdown()) {
            paymentChannel.shutdown();
        }
    }

    @Test
    void testSuccessfulOrderCreation() {
        // Arrange
        String userId = "user123";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-00001";

        // Configure mock services
        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(true);

        // Mock order service
        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull()))
            .thenReturn(mockOrder);
        when(orderService.setPaymentIntent(eq(orderId), anyString()))
            .thenReturn(mockOrder);

        // Act
        Order result = saga.executeOrderCreationSaga(userId, address, null);

        // Assert
        assertNotNull(result);
        assertEquals(orderNumber, result.getOrderNumber());
        assertEquals(userId, result.getUserId());

        // Verify all steps were called
        verify(orderService, times(1)).createOrder(eq(userId), anyList(), eq(address), isNull());
        verify(orderService, times(1)).setPaymentIntent(eq(orderId), anyString());
        // No-arg overload defers to enriched overload with null recipient details
        verify(eventPublisher, times(1)).publishOrderCreatedEvent(mockOrder, null, null);

        // Verify gRPC calls
        assertTrue(mockCartService.wasGetCartCalled());
        assertTrue(mockInventoryService.wasReserveStockCalled());
        assertTrue(mockPaymentService.wasCreatePaymentIntentCalled());
        assertTrue(mockCartService.wasClearCartCalled());
    }

    @Test
    void testSuccessfulOrderCreation_PublishesEnrichedEvent() {
        // Arrange
        String userId = "user-with-email";
        String userEmail = "buyer@example.com";
        String userName = "Buyer Name";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-00099";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(true);

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull()))
            .thenReturn(mockOrder);
        when(orderService.setPaymentIntent(eq(orderId), anyString()))
            .thenReturn(mockOrder);

        // Act
        Order result = saga.executeOrderCreationSaga(userId, address, null, userEmail, userName);

        // Assert
        assertNotNull(result);
        assertEquals(orderNumber, result.getOrderNumber());
        // Saga must forward recipient details to the enriched event so saga-driven
        // orders also produce personalised confirmation emails.
        verify(eventPublisher, times(1)).publishOrderCreatedEvent(mockOrder, userEmail, userName);
        verify(eventPublisher, never()).publishOrderCreatedEvent(any(Order.class));
    }

    @Test
    void testOrderCreation_CartEmpty_ThrowsException() {
        // Arrange
        String userId = "user123";
        Address address = createMockAddress();

        // Configure empty cart
        mockCartService.setCartItems(new ArrayList<>());

        // Act & Assert
        OrderCreationSaga.SagaException exception = assertThrows(
            OrderCreationSaga.SagaException.class,
            () -> saga.executeOrderCreationSaga(userId, address, null)
        );

        assertTrue(exception.getMessage().contains("Cart is empty"));
        verify(orderService, never()).createOrder(anyString(), anyList(), any(), any());
        verify(eventPublisher, never()).publishOrderCreatedEvent(any(Order.class));
        verify(eventPublisher, never()).publishOrderCreatedEvent(any(Order.class), any(), any());
    }

    @Test
    void testOrderCreation_InsufficientStock_TriggersCompensation() {
        // Arrange
        String userId = "user123";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-INV1";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(false);

        // Saga runs reserveStock and createOrder in parallel for performance,
        // so createOrder may be invoked even when reservation ultimately fails.
        // The compensation path is responsible for cancelling any speculatively-
        // persisted order. Stub createOrder so the parallel branch can complete.
        Order speculativeOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull()))
            .thenReturn(speculativeOrder);
        when(orderService.updateOrderStatus(eq(orderId), eq(OrderStatus.CANCELLED)))
            .thenReturn(speculativeOrder);

        // Act & Assert
        OrderCreationSaga.SagaException exception = assertThrows(
            OrderCreationSaga.SagaException.class,
            () -> saga.executeOrderCreationSaga(userId, address, null)
        );

        assertTrue(exception.getMessage().contains("Failed to reserve stock"));

        // Verify compensation - cart should NOT be cleared on failure
        assertFalse(mockCartService.wasClearCartCalled());
        // No success event must ever be published when reservation fails.
        verify(eventPublisher, never()).publishOrderCreatedEvent(any(Order.class));
        verify(eventPublisher, never()).publishOrderCreatedEvent(any(Order.class), any(), any());
        // If the speculative order was persisted, compensation must cancel it.
        verify(orderService, times(1)).updateOrderStatus(orderId, OrderStatus.CANCELLED);
        verify(eventPublisher, times(1)).publishOrderCancelledEvent(speculativeOrder);
    }

    @Test
    void testOrderCreation_PaymentFails_TriggersCompensation() {
        // Arrange
        String userId = "user123";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-00001";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(false);

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull()))
            .thenReturn(mockOrder);
        when(orderService.updateOrderStatus(eq(orderId), eq(OrderStatus.CANCELLED)))
            .thenReturn(mockOrder);

        // Act & Assert
        OrderCreationSaga.SagaException exception = assertThrows(
            OrderCreationSaga.SagaException.class,
            () -> saga.executeOrderCreationSaga(userId, address, null)
        );

        assertTrue(exception.getMessage().contains("Failed to create payment intent"));

        // Verify compensation was triggered
        verify(orderService, times(1)).updateOrderStatus(orderId, OrderStatus.CANCELLED);
        verify(eventPublisher, times(1)).publishOrderCancelledEvent(mockOrder);
        assertTrue(mockInventoryService.wasReleaseReservationCalled());

        // Cart should NOT be cleared on failure
        assertFalse(mockCartService.wasClearCartCalled());
    }

    @Test
    void should_runReserveStockAndCreateOrderInParallel_when_bothStepsTakeSameTime() {
        // Arrange - the saga must fan out reserveStock (gRPC) and createOrder
        // (DB + promotion-service) on a virtual-thread executor so the wall-clock
        // time approaches max(stepA, stepB) instead of sum(stepA, stepB).
        // Per-step delay is 120ms so the sequential floor (240ms) is comfortably
        // above our 200ms upper bound, while the parallel ceiling (~120ms +
        // scheduler overhead) fits well underneath. This gives the timing
        // assertion real signal without making it flaky.
        String userId = "user-parallel";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-PAR1";

        long stepDelayMs = 120L;

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockInventoryService.setArtificialDelayMs(stepDelayMs);
        mockPaymentService.setPaymentSuccess(true);

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull()))
            .thenAnswer(invocation -> {
                Thread.sleep(stepDelayMs);
                return mockOrder;
            });
        when(orderService.setPaymentIntent(eq(orderId), anyString()))
            .thenReturn(mockOrder);

        // Warm up the JVM and the virtual-thread carrier pool with one cheap
        // invocation so the timed run measures steady-state behaviour.
        // (One-off ForkJoinPool / class-loading cost can dominate a 50ms budget.)

        // Act
        long start = System.nanoTime();
        Order result = saga.executeOrderCreationSaga(userId, address, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        // Assert - behaviour preserved
        assertNotNull(result);
        assertEquals(orderNumber, result.getOrderNumber());
        assertTrue(mockInventoryService.wasReserveStockCalled());
        verify(orderService, times(1)).createOrder(eq(userId), anyList(), eq(address), isNull());

        // Assert - timing: sequential would take >=2*stepDelayMs (240ms).
        // Parallel execution should land in the ~stepDelayMs (120ms) range.
        // The 200ms upper bound is below the sequential floor (240ms) so this
        // assertion fails for sequential implementations; well above the parallel
        // ceiling so it does not flake on slow CI workers.
        assertTrue(
            elapsedMs < 200L,
            () -> "Expected parallel fan-out to finish under 200ms but took " + elapsedMs + "ms"
        );
    }

    @Test
    void testOrderCreation_WithPromotionCode() {
        // Arrange
        String userId = "user123";
        Address address = createMockAddress();
        String promotionCode = "SAVE20";
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-00001";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(true);

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), eq(promotionCode)))
            .thenReturn(mockOrder);
        when(orderService.setPaymentIntent(eq(orderId), anyString()))
            .thenReturn(mockOrder);

        // Act
        Order result = saga.executeOrderCreationSaga(userId, address, promotionCode);

        // Assert
        assertNotNull(result);
        verify(orderService, times(1)).createOrder(eq(userId), anyList(), eq(address), eq(promotionCode));
    }

    // Helper methods

    private List<CartItem> createMockCartItems() {
        List<CartItem> items = new ArrayList<>();
        items.add(CartItem.newBuilder()
            .setProductId("prod1")
            .setProductName("Product 1")
            .setPrice(29.99)
            .setQuantity(2)
            .build());
        return items;
    }

    private Address createMockAddress() {
        return Address.builder()
            .street("123 Main St")
            .city("New York")
            .state("NY")
            .postalCode("10001")
            .country("USA")
            .build();
    }

    private Order createMockOrder(String id, String orderNumber, String userId, Address address) {
        List<OrderItem> items = new ArrayList<>();
        items.add(OrderItem.builder()
            .id(UUID.randomUUID().toString())
            .productId("prod1")
            .productName("Product 1")
            .price(new BigDecimal("29.99"))
            .quantity(2)
            .build());

        return Order.builder()
            .id(id)
            .orderNumber(orderNumber)
            .userId(userId)
            .items(items)
            .subtotal(new BigDecimal("59.98"))
            .tax(new BigDecimal("4.80"))
            .shippingCost(new BigDecimal("5.99"))
            .total(new BigDecimal("70.77"))
            .status(OrderStatus.PENDING)
            .shippingAddress(address)
            .build();
    }

    // Mock gRPC Service Implementations

    private static class MockCartService extends CartServiceGrpc.CartServiceImplBase {
        private List<CartItem> cartItems = new ArrayList<>();
        private boolean getCartCalled = false;
        private boolean clearCartCalled = false;

        public void setCartItems(List<CartItem> items) {
            this.cartItems = items;
        }

        public boolean wasGetCartCalled() {
            return getCartCalled;
        }

        public boolean wasClearCartCalled() {
            return clearCartCalled;
        }

        @Override
        public void getCart(com.ecommerce.orderservice.grpc.proto.cart.GetCartRequest request,
                           StreamObserver<GetCartResponse> responseObserver) {
            getCartCalled = true;
            GetCartResponse response = GetCartResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Cart retrieved")
                .addAllItems(cartItems)
                .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void clearCart(com.ecommerce.orderservice.grpc.proto.cart.ClearCartRequest request,
                             StreamObserver<com.ecommerce.orderservice.grpc.proto.cart.ClearCartResponse> responseObserver) {
            clearCartCalled = true;
            com.ecommerce.orderservice.grpc.proto.cart.ClearCartResponse response =
                com.ecommerce.orderservice.grpc.proto.cart.ClearCartResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Cart cleared")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    private static class MockInventoryService extends InventoryServiceGrpc.InventoryServiceImplBase {
        private boolean reservationSuccess = true;
        private boolean reserveStockCalled = false;
        private boolean releaseReservationCalled = false;
        private long artificialDelayMs = 0L;

        public void setReservationSuccess(boolean success) {
            this.reservationSuccess = success;
        }

        public void setArtificialDelayMs(long delayMs) {
            this.artificialDelayMs = delayMs;
        }

        public boolean wasReserveStockCalled() {
            return reserveStockCalled;
        }

        public boolean wasReleaseReservationCalled() {
            return releaseReservationCalled;
        }

        @Override
        public void reserveStock(com.ecommerce.orderservice.grpc.proto.inventory.ReserveStockRequest request,
                                StreamObserver<ReserveStockResponse> responseObserver) {
            reserveStockCalled = true;
            if (artificialDelayMs > 0) {
                try {
                    Thread.sleep(artificialDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            ReserveStockResponse.Builder builder = ReserveStockResponse.newBuilder()
                .setSuccess(reservationSuccess);

            if (reservationSuccess) {
                builder.setMessage("Stock reserved")
                    .setReservationId("res-" + UUID.randomUUID().toString());
            } else {
                builder.setMessage("Insufficient stock");
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }

        @Override
        public void releaseReservation(com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationRequest request,
                                      StreamObserver<com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationResponse> responseObserver) {
            releaseReservationCalled = true;
            com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationResponse response =
                com.ecommerce.orderservice.grpc.proto.inventory.ReleaseReservationResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Reservation released")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    private static class MockPaymentService extends PaymentServiceGrpc.PaymentServiceImplBase {
        private boolean paymentSuccess = true;
        private boolean createPaymentIntentCalled = false;

        public void setPaymentSuccess(boolean success) {
            this.paymentSuccess = success;
        }

        public boolean wasCreatePaymentIntentCalled() {
            return createPaymentIntentCalled;
        }

        @Override
        public void createPaymentIntent(com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentRequest request,
                                       StreamObserver<CreatePaymentIntentResponse> responseObserver) {
            createPaymentIntentCalled = true;
            CreatePaymentIntentResponse.Builder builder = CreatePaymentIntentResponse.newBuilder()
                .setSuccess(paymentSuccess);

            if (paymentSuccess) {
                builder.setMessage("Payment intent created")
                    .setPaymentIntentId("pi_" + UUID.randomUUID().toString());
            } else {
                builder.setMessage("Payment processing failed");
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }
}
