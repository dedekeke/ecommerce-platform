package com.ecommerce.orderservice.saga;

import com.ecommerce.orderservice.client.PromotionServiceClient;
import com.ecommerce.orderservice.client.dto.DiscountResult;
import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.exception.EmptyCartException;
import com.ecommerce.orderservice.grpc.proto.cart.CartItem;
import com.ecommerce.orderservice.grpc.proto.cart.CartServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.cart.GetCartResponse;
import com.ecommerce.orderservice.grpc.proto.inventory.InventoryServiceGrpc;
import com.ecommerce.orderservice.grpc.proto.inventory.ReserveStockResponse;
import com.ecommerce.orderservice.grpc.proto.payment.CreatePaymentIntentResponse;
import com.ecommerce.orderservice.grpc.proto.payment.PaymentServiceGrpc;
import com.ecommerce.orderservice.service.OrderService;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
 * Mockito-level tests for {@link OrderCreationSaga} orchestration: gRPC step
 * wiring, parallel fan-out compensation, and the delegation to the atomic
 * {@link OrderService} operations. Transactional/rollback guarantees (state and
 * event committing together, compensation surviving orchestration failure) are
 * proven separately in {@code OrderCompensationIntegrationTest} against a real
 * transaction manager — a pure-Mockito test cannot observe commit boundaries.
 */
@ExtendWith(MockitoExtension.class)
class OrderCreationSagaTest {

    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Mock
    private OrderService orderService;

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
        mockCartService = new MockCartService();
        mockInventoryService = new MockInventoryService();
        mockPaymentService = new MockPaymentService();

        String cartServerName = InProcessServerBuilder.generateName();
        String inventoryServerName = InProcessServerBuilder.generateName();
        String paymentServerName = InProcessServerBuilder.generateName();

        grpcCleanup.register(
            InProcessServerBuilder.forName(cartServerName)
                .directExecutor().addService(mockCartService).build().start());
        grpcCleanup.register(
            InProcessServerBuilder.forName(inventoryServerName)
                .directExecutor().addService(mockInventoryService).build().start());
        grpcCleanup.register(
            InProcessServerBuilder.forName(paymentServerName)
                .directExecutor().addService(mockPaymentService).build().start());

        cartChannel = grpcCleanup.register(
            InProcessChannelBuilder.forName(cartServerName).directExecutor().build());
        inventoryChannel = grpcCleanup.register(
            InProcessChannelBuilder.forName(inventoryServerName).directExecutor().build());
        paymentChannel = grpcCleanup.register(
            InProcessChannelBuilder.forName(paymentServerName).directExecutor().build());

        saga = new OrderCreationSaga(orderService, promotionServiceClient);
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

    // ---- happy path ---------------------------------------------------------

    @Test
    void testSuccessfulOrderCreation() {
        String userId = "user123";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-00001";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(true);

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull(), isNull()))
            .thenReturn(mockOrder);
        when(orderService.finalizeSuccessfulOrder(eq(orderId), anyString(), anyString(), any(), any()))
            .thenReturn(mockOrder);

        Order result = saga.executeOrderCreationSaga(userId, address, null);

        assertNotNull(result);
        assertEquals(orderNumber, result.getOrderNumber());
        assertEquals(userId, result.getUserId());

        verify(orderService).createOrder(eq(userId), anyList(), eq(address), isNull(), isNull());
        // ORDER_CREATED is now published atomically inside finalizeSuccessfulOrder.
        verify(orderService).finalizeSuccessfulOrder(eq(orderId), anyString(), anyString(), isNull(), isNull());

        assertTrue(mockCartService.wasGetCartCalled());
        assertTrue(mockInventoryService.wasReserveStockCalled());
        assertTrue(mockPaymentService.wasCreatePaymentIntentCalled());
        assertTrue(mockCartService.wasClearCartCalled());
    }

    @Test
    void should_forwardRecipientToFinalize_when_enrichedCheckout() {
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
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull(), isNull()))
            .thenReturn(mockOrder);
        when(orderService.finalizeSuccessfulOrder(eq(orderId), anyString(), anyString(), eq(userEmail), eq(userName)))
            .thenReturn(mockOrder);

        Order result = saga.executeOrderCreationSaga(userId, address, null, userEmail, userName);

        assertNotNull(result);
        verify(orderService).finalizeSuccessfulOrder(eq(orderId), anyString(), anyString(), eq(userEmail), eq(userName));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_surfaceClientSecretAndRealCartItems_when_executeCheckout() {
        // Proves the REST checkout path builds order items from the REAL cart
        // (prod1 x2), not the retired hardcoded "Test Product", and surfaces the
        // PaymentIntent client secret to the caller.
        String userId = "user-checkout";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-CHK1";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(true);

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        ArgumentCaptor<List<OrderItem>> itemsCaptor = ArgumentCaptor.forClass(List.class);
        when(orderService.createOrder(eq(userId), itemsCaptor.capture(), eq(address), isNull(), isNull()))
            .thenReturn(mockOrder);
        when(orderService.finalizeSuccessfulOrder(eq(orderId), anyString(), anyString(), any(), any()))
            .thenReturn(mockOrder);

        OrderCreationSaga.CheckoutResult result =
            saga.executeCheckout(userId, address, null, null, null);

        assertEquals("pi_test_secret", result.paymentClientSecret());
        assertEquals("pi_test", result.paymentIntentId());
        assertEquals("USD", result.currency());
        assertSame(mockOrder, result.order());

        List<OrderItem> capturedItems = itemsCaptor.getValue();
        assertEquals(1, capturedItems.size());
        assertEquals("prod1", capturedItems.get(0).getProductId());
        assertEquals(2, capturedItems.get(0).getQuantity());
    }

    @Test
    void should_threadGuestEmailIntoCreateOrder_when_guestCheckout() {
        // The guest flag is set atomically inside createOrder: the saga must pass
        // the normalized guest email through as the 5th createOrder argument.
        String guestId = "guest:abc123";
        String guestEmail = "guest@example.com";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-GST1";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(true);

        Order mockOrder = createMockOrder(orderId, orderNumber, guestId, address);
        when(orderService.createOrder(eq(guestId), anyList(), eq(address), isNull(), eq(guestEmail)))
            .thenReturn(mockOrder);
        when(orderService.finalizeSuccessfulOrder(eq(orderId), anyString(), anyString(), any(), any()))
            .thenReturn(mockOrder);

        OrderCreationSaga.CheckoutResult result =
            saga.executeCheckout(guestId, address, null, guestEmail, "Guest", guestEmail);

        assertSame(mockOrder, result.order());
        verify(orderService).createOrder(eq(guestId), anyList(), eq(address), isNull(), eq(guestEmail));
    }

    @Test
    void testOrderCreation_WithPromotionCode() {
        String userId = "user123";
        Address address = createMockAddress();
        String promotionCode = "SAVE20";
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-00001";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(true);

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), eq(promotionCode), isNull()))
            .thenReturn(mockOrder);
        when(orderService.finalizeSuccessfulOrder(eq(orderId), anyString(), anyString(), any(), any()))
            .thenReturn(mockOrder);

        Order result = saga.executeOrderCreationSaga(userId, address, promotionCode);

        assertNotNull(result);
        verify(orderService).createOrder(eq(userId), anyList(), eq(address), eq(promotionCode), isNull());
    }

    /**
     * Step 4 redeems the code against the SAME pre-discount amount the code was
     * validated with (the order subtotal); promotion-service re-validates the
     * request before incrementing the usage counter.
     */
    @Test
    void should_applyPromotionWithOrderSubtotal_when_orderCarriesDiscount() {
        String userId = "user-apply";
        Address address = createMockAddress();
        String promotionCode = "SAVE20";
        String orderId = UUID.randomUUID().toString();

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(true);

        Order mockOrder = createMockOrder(orderId, "ORD-2025-00002", userId, address);
        mockOrder.setPromotionCode(promotionCode);
        mockOrder.setDiscountAmount(new BigDecimal("12.00"));
        when(orderService.createOrder(eq(userId), anyList(), eq(address), eq(promotionCode), isNull()))
            .thenReturn(mockOrder);
        when(orderService.finalizeSuccessfulOrder(eq(orderId), anyString(), anyString(), any(), any()))
            .thenReturn(mockOrder);
        when(promotionServiceClient.applyPromotion(anyString(), any(BigDecimal.class)))
            .thenReturn(DiscountResult.builder().valid(true).build());

        saga.executeOrderCreationSaga(userId, address, promotionCode);

        verify(promotionServiceClient).applyPromotion(promotionCode, mockOrder.getSubtotal());
    }

    @Test
    void should_runReserveStockAndCreateOrderInParallel_when_bothStepsTakeSameTime() {
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
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull(), isNull()))
            .thenAnswer(invocation -> {
                Thread.sleep(stepDelayMs);
                return mockOrder;
            });
        when(orderService.finalizeSuccessfulOrder(eq(orderId), anyString(), anyString(), any(), any()))
            .thenReturn(mockOrder);

        long start = System.nanoTime();
        Order result = saga.executeOrderCreationSaga(userId, address, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertNotNull(result);
        assertEquals(orderNumber, result.getOrderNumber());
        assertTrue(mockInventoryService.wasReserveStockCalled());
        verify(orderService).createOrder(eq(userId), anyList(), eq(address), isNull(), isNull());

        assertTrue(
            elapsedMs < 200L,
            () -> "Expected parallel fan-out to finish under 200ms but took " + elapsedMs + "ms"
        );
    }

    // ---- empty cart ---------------------------------------------------------

    @Test
    void should_throwEmptyCart_when_cartHasNoItems() {
        String userId = "user123";
        Address address = createMockAddress();
        mockCartService.setCartItems(new ArrayList<>());

        // Empty cart is a client condition (400), NOT a retryable saga failure.
        assertThrows(EmptyCartException.class,
            () -> saga.executeOrderCreationSaga(userId, address, null));

        verify(orderService, never()).createOrder(anyString(), anyList(), any(), any(), any());
        verify(orderService, never()).compensateCancelOrder(anyString());
    }

    // ---- compensation: downstream returns success=false --------------------

    @Test
    void testOrderCreation_InsufficientStock_TriggersCompensation() {
        String userId = "user123";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-INV1";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(false);

        // Fan-out means createOrder may still run when reservation fails; the
        // speculatively-persisted order must be compensated.
        Order speculativeOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull(), isNull()))
            .thenReturn(speculativeOrder);

        OrderCreationSaga.SagaException exception = assertThrows(
            OrderCreationSaga.SagaException.class,
            () -> saga.executeOrderCreationSaga(userId, address, null));

        assertTrue(exception.getMessage().contains("Failed to reserve stock"));
        assertFalse(mockCartService.wasClearCartCalled());
        // Compensation cancels the created order atomically (state + event).
        verify(orderService).compensateCancelOrder(orderId);
        // No live reservation existed, so nothing to release.
        assertFalse(mockInventoryService.wasReleaseReservationCalled());
    }

    @Test
    void testOrderCreation_PaymentFails_TriggersCompensation() {
        String userId = "user123";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-00001";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        mockPaymentService.setPaymentSuccess(false);

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull(), isNull()))
            .thenReturn(mockOrder);

        OrderCreationSaga.SagaException exception = assertThrows(
            OrderCreationSaga.SagaException.class,
            () -> saga.executeOrderCreationSaga(userId, address, null));

        assertTrue(exception.getMessage().contains("Failed to create payment intent"));
        // Both resources undone: reservation released AND order cancelled.
        verify(orderService).compensateCancelOrder(orderId);
        assertTrue(mockInventoryService.wasReleaseReservationCalled());
        assertFalse(mockCartService.wasClearCartCalled());
    }

    // ---- compensation: a parallel leg THROWS (not success=false) -----------

    @Test
    void should_releaseReservation_when_createOrderThrows() {
        // createOrder throws while reserveStock succeeds — the live reservation
        // must still be released even though `order` was never assigned.
        String userId = "user123";
        Address address = createMockAddress();

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setReservationSuccess(true);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull(), isNull()))
            .thenThrow(new RuntimeException("order DB write failed"));

        assertThrows(OrderCreationSaga.SagaException.class,
            () -> saga.executeOrderCreationSaga(userId, address, null));

        assertTrue(mockInventoryService.wasReleaseReservationCalled(),
            "reservation from the succeeded sibling must be released");
        verify(orderService, never()).compensateCancelOrder(anyString());
    }

    @Test
    void should_cancelOrder_when_reserveStockThrows() {
        // reserveStock throws (gRPC UNAVAILABLE) while createOrder succeeds — the
        // created order must still be cancelled even though reserveStock never
        // returned a reservation id.
        String userId = "user123";
        Address address = createMockAddress();
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-THR1";

        mockCartService.setCartItems(createMockCartItems());
        mockInventoryService.setFailWithError(true);

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, address);
        when(orderService.createOrder(eq(userId), anyList(), eq(address), isNull(), isNull()))
            .thenReturn(mockOrder);

        assertThrows(OrderCreationSaga.SagaException.class,
            () -> saga.executeOrderCreationSaga(userId, address, null));

        verify(orderService).compensateCancelOrder(orderId);
        assertFalse(mockInventoryService.wasReleaseReservationCalled(),
            "no reservation was acquired, so none should be released");
        assertFalse(mockCartService.wasClearCartCalled());
    }

    // ---- helpers ------------------------------------------------------------

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

    // ---- mock gRPC services -------------------------------------------------

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
        private boolean failWithError = false;
        private long artificialDelayMs = 0L;

        public void setReservationSuccess(boolean success) {
            this.reservationSuccess = success;
        }

        public void setArtificialDelayMs(long delayMs) {
            this.artificialDelayMs = delayMs;
        }

        /** Simulate a gRPC transport failure (server-side error) rather than a success=false body. */
        public void setFailWithError(boolean failWithError) {
            this.failWithError = failWithError;
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
            if (failWithError) {
                responseObserver.onError(
                    Status.UNAVAILABLE.withDescription("inventory-service down").asRuntimeException());
                return;
            }
            ReserveStockResponse.Builder builder = ReserveStockResponse.newBuilder()
                .setSuccess(reservationSuccess);

            if (reservationSuccess) {
                builder.setMessage("Stock reserved")
                    .setReservationId("res-" + UUID.randomUUID());
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
                    .setPaymentIntentId("pi_test")
                    .setClientSecret("pi_test_secret");
            } else {
                builder.setMessage("Payment processing failed");
            }

            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        }
    }
}
