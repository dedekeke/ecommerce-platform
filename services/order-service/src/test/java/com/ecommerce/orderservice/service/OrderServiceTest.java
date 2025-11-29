package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.exception.InvalidOrderStatusTransitionException;
import com.ecommerce.orderservice.exception.OrderNotFoundException;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderService
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderNumberGeneratorService orderNumberGenerator;

    @InjectMocks
    private OrderService orderService;

    private String userId;
    private Address shippingAddress;
    private List<OrderItem> orderItems;

    @BeforeEach
    void setUp() {
        // Set configuration values
        ReflectionTestUtils.setField(orderService, "taxRate", 0.08);
        ReflectionTestUtils.setField(orderService, "shippingBaseCost", 5.99);
        ReflectionTestUtils.setField(orderService, "freeShippingThreshold", 50.00);

        userId = "user123";

        shippingAddress = Address.builder()
            .street("123 Main St")
            .city("San Francisco")
            .state("CA")
            .postalCode("94105")
            .country("USA")
            .build();

        orderItems = new ArrayList<>();
        OrderItem item1 = OrderItem.builder()
            .productId("prod1")
            .productName("Product 1")
            .price(BigDecimal.valueOf(25.00))
            .quantity(2)
            .build();
        item1.calculateSubtotal();

        orderItems.add(item1);
    }

    @Test
    void testCreateOrder_Success() {
        // Arrange
        String orderNumber = "ORD-2025-00001";
        when(orderNumberGenerator.generateOrderNumber()).thenReturn(orderNumber);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Order order = orderService.createOrder(userId, orderItems, shippingAddress, null);

        // Assert
        assertNotNull(order);
        assertEquals(orderNumber, order.getOrderNumber());
        assertEquals(userId, order.getUserId());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals(1, order.getItems().size());
        assertEquals(0, BigDecimal.valueOf(50.00).compareTo(order.getSubtotal())); // 25 * 2
        assertEquals(0, BigDecimal.valueOf(4.00).compareTo(order.getTax())); // 50 * 0.08
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getShippingCost())); // Free shipping over 50

        verify(orderNumberGenerator, times(1)).generateOrderNumber();
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testCreateOrder_WithShippingCost() {
        // Arrange - order under free shipping threshold
        orderItems.get(0).setQuantity(1); // $25 total
        orderItems.get(0).calculateSubtotal();

        String orderNumber = "ORD-2025-00002";
        when(orderNumberGenerator.generateOrderNumber()).thenReturn(orderNumber);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Order order = orderService.createOrder(userId, orderItems, shippingAddress, null);

        // Assert
        assertEquals(0, BigDecimal.valueOf(25.00).compareTo(order.getSubtotal()));
        assertEquals(0, BigDecimal.valueOf(5.99).compareTo(order.getShippingCost()));
    }

    @Test
    void testGetOrder_Success() {
        // Arrange
        Order mockOrder = Order.builder()
            .id("order123")
            .userId(userId)
            .orderNumber("ORD-2025-00001")
            .build();

        when(orderRepository.findById("order123")).thenReturn(Optional.of(mockOrder));

        // Act
        Order order = orderService.getOrder("order123", userId);

        // Assert
        assertNotNull(order);
        assertEquals("order123", order.getId());
        verify(orderRepository, times(1)).findById("order123");
    }

    @Test
    void testGetOrder_NotFound() {
        // Arrange
        when(orderRepository.findById("nonexistent")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(OrderNotFoundException.class, () ->
            orderService.getOrder("nonexistent", userId)
        );
    }

    @Test
    void testGetOrder_WrongUser() {
        // Arrange
        Order mockOrder = Order.builder()
            .id("order123")
            .userId("differentUser")
            .orderNumber("ORD-2025-00001")
            .build();

        when(orderRepository.findById("order123")).thenReturn(Optional.of(mockOrder));

        // Act & Assert
        assertThrows(OrderNotFoundException.class, () ->
            orderService.getOrder("order123", userId)
        );
    }

    @Test
    void testUpdateOrderStatus_ValidTransition() {
        // Arrange
        Order mockOrder = Order.builder()
            .id("order123")
            .userId(userId)
            .orderNumber("ORD-2025-00001")
            .status(OrderStatus.PENDING)
            .build();

        when(orderRepository.findById("order123")).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Order updatedOrder = orderService.updateOrderStatus("order123", OrderStatus.CONFIRMED);

        // Assert
        assertEquals(OrderStatus.CONFIRMED, updatedOrder.getStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testUpdateOrderStatus_InvalidTransition() {
        // Arrange
        Order mockOrder = Order.builder()
            .id("order123")
            .userId(userId)
            .orderNumber("ORD-2025-00001")
            .status(OrderStatus.PENDING)
            .build();

        when(orderRepository.findById("order123")).thenReturn(Optional.of(mockOrder));

        // Act & Assert
        assertThrows(InvalidOrderStatusTransitionException.class, () ->
            orderService.updateOrderStatus("order123", OrderStatus.DELIVERED)
        );
    }

    @Test
    void testCancelOrder_Success() {
        // Arrange
        Order mockOrder = Order.builder()
            .id("order123")
            .userId(userId)
            .orderNumber("ORD-2025-00001")
            .status(OrderStatus.PENDING)
            .build();

        when(orderRepository.findById("order123")).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Order cancelledOrder = orderService.cancelOrder("order123", userId, "Customer request");

        // Assert
        assertEquals(OrderStatus.CANCELLED, cancelledOrder.getStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testCancelOrder_CannotCancel() {
        // Arrange - order already shipped
        Order mockOrder = Order.builder()
            .id("order123")
            .userId(userId)
            .orderNumber("ORD-2025-00001")
            .status(OrderStatus.SHIPPED)
            .build();

        when(orderRepository.findById("order123")).thenReturn(Optional.of(mockOrder));

        // Act & Assert
        assertThrows(InvalidOrderStatusTransitionException.class, () ->
            orderService.cancelOrder("order123", userId, "Customer request")
        );
    }

    @Test
    void testApplyDiscount() {
        // Arrange
        Order mockOrder = Order.builder()
            .id("order123")
            .userId(userId)
            .orderNumber("ORD-2025-00001")
            .subtotal(BigDecimal.valueOf(100.00))
            .tax(BigDecimal.valueOf(8.00))
            .shippingCost(BigDecimal.ZERO)
            .total(BigDecimal.valueOf(108.00))
            .items(new ArrayList<>())
            .build();

        when(orderRepository.findById("order123")).thenReturn(Optional.of(mockOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Order discountedOrder = orderService.applyDiscount("order123", BigDecimal.valueOf(10.00));

        // Assert
        assertEquals(0, BigDecimal.valueOf(10.00).compareTo(discountedOrder.getDiscountAmount()));
        verify(orderRepository, times(1)).save(any(Order.class));
    }
}
