package com.ecommerce.orderservice.grpc;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.event.OrderEventPublisher;
import com.ecommerce.orderservice.grpc.proto.*;
import com.ecommerce.orderservice.repository.OrderRepository;
import com.ecommerce.orderservice.service.OrderService;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for OrderGrpcServiceImpl
 * Uses in-process gRPC server for testing
 */
@ExtendWith(MockitoExtension.class)
class OrderGrpcServiceIntegrationTest {

    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Mock
    private OrderService orderService;

    @Mock
    private OrderEventPublisher eventPublisher;

    @Mock
    private OrderRepository orderRepository;

    private OrderServiceGrpc.OrderServiceBlockingStub blockingStub;
    private ManagedChannel channel;

    @BeforeEach
    void setUp() throws Exception {
        // Generate a unique in-process server name
        String serverName = InProcessServerBuilder.generateName();

        // Create and start the in-process server
        grpcCleanup.register(
            InProcessServerBuilder
                .forName(serverName)
                .directExecutor()
                .addService(new OrderGrpcServiceImpl(orderService, eventPublisher))
                .build()
                .start()
        );

        // Create a client channel and stub
        channel = grpcCleanup.register(
            InProcessChannelBuilder
                .forName(serverName)
                .directExecutor()
                .build()
        );

        blockingStub = OrderServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
        }
    }

    @Test
    void testCreateOrder_Success() {
        // Arrange
        String userId = "user123";
        String orderId = UUID.randomUUID().toString();
        String orderNumber = "ORD-2025-00001";

        List<OrderItem> items = createMockOrderItems();
        Address address = createMockAddress();
        Order mockOrder = createMockOrder(orderId, orderNumber, userId, items, address);

        when(orderService.createOrder(eq(userId), anyList(), any(Address.class), any()))
            .thenReturn(mockOrder);

        // Create request
        CreateOrderRequest request = CreateOrderRequest.newBuilder()
            .setUserId(userId)
            .addItems(OrderItemRequest.newBuilder()
                .setProductId("prod1")
                .setProductName("Product 1")
                .setPrice(29.99)
                .setQuantity(2)
                .build())
            .setShippingAddress(com.ecommerce.orderservice.grpc.proto.Address.newBuilder()
                .setStreet("123 Main St")
                .setCity("New York")
                .setState("NY")
                .setPostalCode("10001")
                .setCountry("USA")
                .build())
            .setPromotionCode("")
            .build();

        // Act
        CreateOrderResponse response = blockingStub.createOrder(request);

        // Assert
        assertTrue(response.getSuccess(), "Response should be successful but was: " + response.getMessage());
        assertEquals("Order created successfully", response.getMessage());
        assertNotNull(response.getOrder());
        assertEquals(orderNumber, response.getOrder().getOrderNumber());
        assertEquals(userId, response.getOrder().getUserId());
        assertEquals(1, response.getOrder().getItemsCount());

        verify(orderService, times(1)).createOrder(eq(userId), anyList(), any(Address.class), any());
        verify(eventPublisher, times(1)).publishOrderCreatedEvent(any(Order.class));
    }

    @Test
    void testCreateOrder_Failure() {
        // Arrange
        String userId = "user123";
        when(orderService.createOrder(eq(userId), anyList(), any(Address.class), any()))
            .thenThrow(new RuntimeException("Insufficient stock"));

        CreateOrderRequest request = CreateOrderRequest.newBuilder()
            .setUserId(userId)
            .addItems(OrderItemRequest.newBuilder()
                .setProductId("prod1")
                .setProductName("Product 1")
                .setPrice(29.99)
                .setQuantity(2)
                .build())
            .setShippingAddress(com.ecommerce.orderservice.grpc.proto.Address.newBuilder()
                .setStreet("123 Main St")
                .setCity("New York")
                .setState("NY")
                .setPostalCode("10001")
                .setCountry("USA")
                .build())
            .build();

        // Act
        CreateOrderResponse response = blockingStub.createOrder(request);

        // Assert
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("Insufficient stock"));
        verify(eventPublisher, never()).publishOrderCreatedEvent(any());
    }

    @Test
    void testGetOrder_Success() {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        String userId = "user123";
        String orderNumber = "ORD-2025-00001";

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, createMockOrderItems(), createMockAddress());
        when(orderService.getOrder(orderId, userId)).thenReturn(mockOrder);

        GetOrderRequest request = GetOrderRequest.newBuilder()
            .setOrderId(orderId)
            .setUserId(userId)
            .build();

        // Act
        GetOrderResponse response = blockingStub.getOrder(request);

        // Assert
        assertTrue(response.getSuccess(), "Response should be successful but was: " + response.getMessage());
        assertEquals("Order retrieved successfully", response.getMessage());
        assertNotNull(response.getOrder());
        assertEquals(orderNumber, response.getOrder().getOrderNumber());
        assertEquals(userId, response.getOrder().getUserId());

        verify(orderService, times(1)).getOrder(orderId, userId);
    }

    @Test
    void testUpdateOrderStatus_Success() {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        String userId = "user123";
        String orderNumber = "ORD-2025-00001";

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, createMockOrderItems(), createMockAddress());
        mockOrder.setStatus(OrderStatus.PROCESSING);

        when(orderService.updateOrderStatus(orderId, OrderStatus.PROCESSING))
            .thenReturn(mockOrder);

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.newBuilder()
            .setOrderId(orderId)
            .setStatus(com.ecommerce.orderservice.grpc.proto.OrderStatus.PROCESSING)
            .build();

        // Act
        UpdateOrderStatusResponse response = blockingStub.updateOrderStatus(request);

        // Assert
        assertTrue(response.getSuccess(), "Response should be successful but was: " + response.getMessage());
        assertEquals("Order status updated successfully", response.getMessage());
        assertNotNull(response.getOrder());
        assertEquals(com.ecommerce.orderservice.grpc.proto.OrderStatus.PROCESSING, response.getOrder().getStatus());

        verify(orderService, times(1)).updateOrderStatus(orderId, OrderStatus.PROCESSING);
        verify(eventPublisher, times(1)).publishOrderUpdatedEvent(any(Order.class));
    }

    @Test
    void testUpdateOrderStatus_ToDelivered_PublishesCompletedEvent() {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        String userId = "user123";
        String orderNumber = "ORD-2025-00001";

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, createMockOrderItems(), createMockAddress());
        mockOrder.setStatus(OrderStatus.DELIVERED);

        when(orderService.updateOrderStatus(orderId, OrderStatus.DELIVERED))
            .thenReturn(mockOrder);

        UpdateOrderStatusRequest request = UpdateOrderStatusRequest.newBuilder()
            .setOrderId(orderId)
            .setStatus(com.ecommerce.orderservice.grpc.proto.OrderStatus.DELIVERED)
            .build();

        // Act
        UpdateOrderStatusResponse response = blockingStub.updateOrderStatus(request);

        // Assert
        assertTrue(response.getSuccess(), "Response should be successful but was: " + response.getMessage());
        verify(eventPublisher, times(1)).publishOrderUpdatedEvent(any(Order.class));
        verify(eventPublisher, times(1)).publishOrderCompletedEvent(any(Order.class));
    }

    @Test
    void testCancelOrder_Success() {
        // Arrange
        String orderId = UUID.randomUUID().toString();
        String userId = "user123";
        String orderNumber = "ORD-2025-00001";
        String reason = "Changed mind";

        Order mockOrder = createMockOrder(orderId, orderNumber, userId, createMockOrderItems(), createMockAddress());
        mockOrder.setStatus(OrderStatus.CANCELLED);

        when(orderService.cancelOrder(orderId, userId, reason))
            .thenReturn(mockOrder);

        CancelOrderRequest request = CancelOrderRequest.newBuilder()
            .setOrderId(orderId)
            .setUserId(userId)
            .setReason(reason)
            .build();

        // Act
        CancelOrderResponse response = blockingStub.cancelOrder(request);

        // Assert
        assertTrue(response.getSuccess());
        assertEquals("Order cancelled successfully", response.getMessage());

        verify(orderService, times(1)).cancelOrder(orderId, userId, reason);
        verify(eventPublisher, times(1)).publishOrderCancelledEvent(any(Order.class));
    }

    @Test
    void testGetUserOrders_Success() {
        // Arrange
        String userId = "user123";
        List<Order> orders = List.of(
            createMockOrder(UUID.randomUUID().toString(), "ORD-2025-00001", userId, createMockOrderItems(), createMockAddress()),
            createMockOrder(UUID.randomUUID().toString(), "ORD-2025-00002", userId, createMockOrderItems(), createMockAddress())
        );
        Page<Order> orderPage = new PageImpl<>(orders, PageRequest.of(0, 10), 2);

        when(orderService.getUserOrders(eq(userId), any(Pageable.class)))
            .thenReturn(orderPage);

        GetUserOrdersRequest request = GetUserOrdersRequest.newBuilder()
            .setUserId(userId)
            .setPage(0)
            .setSize(10)
            .build();

        // Act
        GetUserOrdersResponse response = blockingStub.getUserOrders(request);

        // Assert
        assertTrue(response.getSuccess(), "Response should be successful but was: " + response.getMessage());
        assertEquals("Orders retrieved successfully", response.getMessage());
        assertEquals(2, response.getOrdersCount());
        assertEquals(2, response.getTotalElements());
        assertEquals(1, response.getTotalPages());

        verify(orderService, times(1)).getUserOrders(eq(userId), any(Pageable.class));
    }

    // Helper methods

    private List<OrderItem> createMockOrderItems() {
        List<OrderItem> items = new ArrayList<>();
        BigDecimal price = new BigDecimal("29.99");
        int quantity = 2;
        OrderItem item1 = OrderItem.builder()
            .id(UUID.randomUUID().toString())
            .productId("prod1")
            .productName("Product 1")
            .price(price)
            .quantity(quantity)
            .build();
        // Manually calculate subtotal since @PrePersist won't run on mock objects
        item1.setSubtotal(price.multiply(BigDecimal.valueOf(quantity)));
        items.add(item1);
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

    private Order createMockOrder(String id, String orderNumber, String userId,
                                  List<OrderItem> items, Address address) {
        Order order = Order.builder()
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
            .paymentIntentId("pi_123456")
            .build();

        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        return order;
    }
}
