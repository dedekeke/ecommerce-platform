package com.ecommerce.orderservice.service;

import com.ecommerce.orderservice.client.PromotionServiceClient;
import com.ecommerce.orderservice.client.dto.DiscountResult;
import com.ecommerce.orderservice.client.dto.PromotionValidationRequest;
import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.event.OrderEventPublisher;
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

    @Mock
    private PromotionServiceClient promotionServiceClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private com.ecommerce.orderservice.shipping.ShippingProvider shippingProvider;

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
    void should_applyLoyaltyDiscountAndReduceTotal_when_userHasTier() {
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-2026-00100");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        // 10% loyalty tier on a $50 subtotal -> $5 off.
        when(promotionServiceClient.getLoyaltyDiscountPercent(userId))
            .thenReturn(BigDecimal.valueOf(10));

        Order order = orderService.createOrder(userId, orderItems, shippingAddress, null);

        assertEquals(0, BigDecimal.valueOf(5.00).compareTo(order.getLoyaltyDiscount()));
        // Tax is computed on the post-discount taxable amount (45 = 50 - 5),
        // so tax = 45 * 0.08 = 3.60.
        assertEquals(0, BigDecimal.valueOf(3.60).compareTo(order.getTax()));
        // subtotal 50 - loyalty 5 + tax 3.60 + shipping 0 (free over 50) = 48.60
        assertEquals(0, BigDecimal.valueOf(48.60).compareTo(order.getTotal()));
    }

    @Test
    void should_leaveLoyaltyDiscountNull_when_noTierDiscount() {
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-2026-00101");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(promotionServiceClient.getLoyaltyDiscountPercent(userId))
            .thenReturn(BigDecimal.ZERO);

        Order order = orderService.createOrder(userId, orderItems, shippingAddress, null);

        assertNull(order.getLoyaltyDiscount());
        assertEquals(0, BigDecimal.valueOf(54.00).compareTo(order.getTotal()));
    }

    @Test
    void should_applyLoyaltyOnPostPromotionSubtotal_when_bothDiscountsPresent() {
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-2026-00102");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(promotionServiceClient.validatePromotion(any(PromotionValidationRequest.class)))
            .thenReturn(DiscountResult.builder()
                .valid(true)
                .discountAmount(BigDecimal.valueOf(10.00))
                .build());
        // 10% loyalty applies to (50 - 10) = 40 -> $4 off.
        when(promotionServiceClient.getLoyaltyDiscountPercent(userId))
            .thenReturn(BigDecimal.valueOf(10));

        Order order = orderService.createOrder(userId, orderItems, shippingAddress, "SAVE10");

        assertEquals(0, BigDecimal.valueOf(4.00).compareTo(order.getLoyaltyDiscount()));
        // Tax base = postPromotionSubtotal - loyalty = (50 - 10) - 4 = 36,
        // so tax = 36 * 0.08 = 2.88.
        assertEquals(0, BigDecimal.valueOf(2.88).compareTo(order.getTax()));
        // 50 - 10 (promo) - 4 (loyalty) + 2.88 (tax) + 0 (shipping) = 38.88
        assertEquals(0, BigDecimal.valueOf(38.88).compareTo(order.getTotal()));
    }

    @Test
    void should_persistIntentSecretAndPublishCreated_when_finalizingSuccessfulOrder() {
        Order pending = pendingOrder("order-1", "ORD-2026-9001");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(pending));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order result = orderService.finalizeSuccessfulOrder(
            "order-1", "pi_1", "pi_1_secret", "buyer@example.com", "Buyer");

        assertEquals("pi_1", result.getPaymentIntentId());
        assertEquals("pi_1_secret", result.getPaymentClientSecret());
        verify(orderEventPublisher).publishOrderCreatedEvent(result, "buyer@example.com", "Buyer");
    }

    @Test
    void should_cancelAndPublishCancelled_when_compensating() {
        Order pending = pendingOrder("order-2", "ORD-2026-9002");
        when(orderRepository.findById("order-2")).thenReturn(Optional.of(pending));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        orderService.compensateCancelOrder("order-2");

        assertEquals(OrderStatus.CANCELLED, pending.getStatus());
        verify(orderEventPublisher).publishOrderCancelledEvent(pending);
    }

    @Test
    void should_beNoOp_when_compensatingAlreadyCancelledOrder() {
        Order cancelled = pendingOrder("order-3", "ORD-2026-9003");
        cancelled.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById("order-3")).thenReturn(Optional.of(cancelled));

        orderService.compensateCancelOrder("order-3");

        verify(orderRepository, never()).save(any(Order.class));
        verify(orderEventPublisher, never()).publishOrderCancelledEvent(any(Order.class));
    }

    private Order pendingOrder(String id, String orderNumber) {
        return Order.builder()
            .id(id)
            .orderNumber(orderNumber)
            .userId(userId)
            .subtotal(BigDecimal.valueOf(50.00))
            .tax(BigDecimal.valueOf(4.00))
            .shippingCost(BigDecimal.ZERO)
            .total(BigDecimal.valueOf(54.00))
            .status(OrderStatus.PENDING)
            .shippingAddress(shippingAddress)
            .build();
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
    void should_setShipmentFieldsAndPublishEvent_when_markingConfirmedOrderShipped() {
        Order confirmed = pendingOrder("order-ship-1", "ORD-2026-9100");
        confirmed.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById("order-ship-1")).thenReturn(Optional.of(confirmed));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(shippingProvider.createShipment(any()))
            .thenReturn(new com.ecommerce.orderservice.shipping.ShippingProvider.Shipment("UPS", "1Z999"));

        Order shipped = orderService.markOrderShipped("order-ship-1", "UPS", "1Z999");

        assertEquals(OrderStatus.SHIPPED, shipped.getStatus());
        assertEquals("UPS", shipped.getCarrier());
        assertEquals("1Z999", shipped.getTrackingNumber());
        assertNotNull(shipped.getShippedAt());
        verify(orderEventPublisher, times(1)).publishOrderShippedEvent(shipped);
    }

    @Test
    void should_useProviderReturnedTracking_when_markingShipped() {
        Order processing = pendingOrder("order-ship-2", "ORD-2026-9101");
        processing.setStatus(OrderStatus.PROCESSING);
        when(orderRepository.findById("order-ship-2")).thenReturn(Optional.of(processing));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        // Provider is authoritative: it can override the operator hint.
        when(shippingProvider.createShipment(any()))
            .thenReturn(new com.ecommerce.orderservice.shipping.ShippingProvider.Shipment("DHL", "CARRIER-ASSIGNED"));

        Order shipped = orderService.markOrderShipped("order-ship-2", "DHL", "operator-hint");

        assertEquals("CARRIER-ASSIGNED", shipped.getTrackingNumber());
        assertEquals("DHL", shipped.getCarrier());
    }

    @Test
    void should_rejectAndNotPublish_when_markingPendingOrderShipped() {
        Order pending = pendingOrder("order-ship-3", "ORD-2026-9102");
        when(orderRepository.findById("order-ship-3")).thenReturn(Optional.of(pending));

        assertThrows(InvalidOrderStatusTransitionException.class, () ->
            orderService.markOrderShipped("order-ship-3", "UPS", "1Z999"));

        verify(orderRepository, never()).save(any(Order.class));
        verify(orderEventPublisher, never()).publishOrderShippedEvent(any(Order.class));
        verifyNoInteractions(shippingProvider);
    }

    @Test
    void should_setDeliveredFields_when_markingShippedOrderDelivered() {
        Order shipped = pendingOrder("order-del-1", "ORD-2026-9200");
        shipped.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findById("order-del-1")).thenReturn(Optional.of(shipped));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order delivered = orderService.markOrderDelivered("order-del-1");

        assertEquals(OrderStatus.DELIVERED, delivered.getStatus());
        assertNotNull(delivered.getDeliveredAt());
    }

    @Test
    void should_reject_when_markingNonShippedOrderDelivered() {
        Order confirmed = pendingOrder("order-del-2", "ORD-2026-9201");
        confirmed.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findById("order-del-2")).thenReturn(Optional.of(confirmed));

        assertThrows(InvalidOrderStatusTransitionException.class, () ->
            orderService.markOrderDelivered("order-del-2"));

        verify(orderRepository, never()).save(any(Order.class));
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

    @Test
    void testCreateOrder_WithValidPromotion() {
        // Arrange
        String orderNumber = "ORD-2025-00003";
        String promotionCode = "SAVE20";

        when(orderNumberGenerator.generateOrderNumber()).thenReturn(orderNumber);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Mock promotion validation
        DiscountResult discountResult = DiscountResult.builder()
            .valid(true)
            .message("Promotion applied successfully")
            .discountAmount(BigDecimal.valueOf(10.00))
            .finalAmount(BigDecimal.valueOf(40.00))
            .promotionCode(promotionCode)
            .promotionName("20% Off")
            .build();

        when(promotionServiceClient.validatePromotion(any(PromotionValidationRequest.class)))
            .thenReturn(discountResult);

        // Act
        Order order = orderService.createOrder(userId, orderItems, shippingAddress, promotionCode);

        // Assert
        assertNotNull(order);
        assertEquals(promotionCode, order.getPromotionCode());
        assertEquals(0, BigDecimal.valueOf(10.00).compareTo(order.getDiscountAmount()));

        // Verify promotion validation was called
        verify(promotionServiceClient, times(1)).validatePromotion(any(PromotionValidationRequest.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testCreateOrder_WithInvalidPromotion() {
        // Arrange
        String orderNumber = "ORD-2025-00004";
        String promotionCode = "INVALID";

        when(orderNumberGenerator.generateOrderNumber()).thenReturn(orderNumber);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Mock invalid promotion
        DiscountResult discountResult = DiscountResult.builder()
            .valid(false)
            .message("Promotion code not found")
            .discountAmount(BigDecimal.ZERO)
            .finalAmount(BigDecimal.valueOf(50.00))
            .build();

        when(promotionServiceClient.validatePromotion(any(PromotionValidationRequest.class)))
            .thenReturn(discountResult);

        // Act
        Order order = orderService.createOrder(userId, orderItems, shippingAddress, promotionCode);

        // Assert
        assertNotNull(order);
        assertNull(order.getPromotionCode()); // Invalid promotion should not be saved
        assertNull(order.getDiscountAmount()); // No discount applied

        // Verify promotion validation was called
        verify(promotionServiceClient, times(1)).validatePromotion(any(PromotionValidationRequest.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testCreateOrder_PromotionServiceUnavailable() {
        // Arrange
        String orderNumber = "ORD-2025-00005";
        String promotionCode = "SAVE20";

        when(orderNumberGenerator.generateOrderNumber()).thenReturn(orderNumber);
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Mock promotion service unavailable
        DiscountResult discountResult = DiscountResult.builder()
            .valid(false)
            .message("Promotion service is currently unavailable")
            .discountAmount(BigDecimal.ZERO)
            .finalAmount(BigDecimal.valueOf(50.00))
            .build();

        when(promotionServiceClient.validatePromotion(any(PromotionValidationRequest.class)))
            .thenReturn(discountResult);

        // Act
        Order order = orderService.createOrder(userId, orderItems, shippingAddress, promotionCode);

        // Assert - Order should still be created without discount
        assertNotNull(order);
        assertNull(order.getPromotionCode());
        assertNull(order.getDiscountAmount());

        verify(promotionServiceClient, times(1)).validatePromotion(any(PromotionValidationRequest.class));
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    // ---- guest checkout: flag + claim --------------------------------------

    @Test
    void should_flagGuestOrderAtomically_when_createOrderWithGuestEmail() {
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-2026-0500");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // A non-null guestEmail flags the order + persists the claim key as part of
        // the same INSERT — there is no separate markAsGuestOrder step to fail.
        Order order = orderService.createOrder(
            "guest:hash", orderItems, shippingAddress, null, "guest@example.com");

        assertTrue(order.isGuestOrder());
        assertEquals("guest@example.com", order.getGuestEmail());
    }

    @Test
    void should_leaveGuestFlagsUnset_when_createOrderWithoutGuestEmail() {
        when(orderNumberGenerator.generateOrderNumber()).thenReturn("ORD-2026-0501");
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Order order = orderService.createOrder(userId, orderItems, shippingAddress, null);

        assertFalse(order.isGuestOrder());
        assertNull(order.getGuestEmail());
    }

    @Test
    void should_flagOrderAndPersistClaimKey_when_markAsGuestOrder() {
        Order order = new Order();
        order.setId("order-guest-1");
        order.setOrderNumber("ORD-2026-0009");
        when(orderRepository.findById("order-guest-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.markAsGuestOrder("order-guest-1", "guest@example.com");

        assertTrue(result.isGuestOrder());
        assertEquals("guest@example.com", result.getGuestEmail());
    }

    @Test
    void should_throw_when_markAsGuestOrderMissingOrder() {
        when(orderRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(OrderNotFoundException.class,
            () -> orderService.markAsGuestOrder("nope", "guest@example.com"));
    }

    @Test
    void should_relinkGuestOrdersToUser_when_claimGuestOrders() {
        Order o1 = new Order();
        o1.setId("g1");
        o1.setUserId("guest:hash");
        o1.setGuestOrder(true);
        o1.setGuestEmail("guest@example.com");
        Order o2 = new Order();
        o2.setId("g2");
        o2.setUserId("guest:hash");
        o2.setGuestOrder(true);
        o2.setGuestEmail("guest@example.com");
        when(orderRepository.findByGuestEmailAndGuestOrderTrue("guest@example.com"))
            .thenReturn(new ArrayList<>(List.of(o1, o2)));

        int claimed = orderService.claimGuestOrders("guest@example.com", "auth0|real-user");

        assertEquals(2, claimed);
        assertEquals("auth0|real-user", o1.getUserId());
        assertEquals("auth0|real-user", o2.getUserId());
        // Provenance retained for audit.
        assertTrue(o1.isGuestOrder());
        assertEquals("guest@example.com", o1.getGuestEmail());
        verify(orderRepository).saveAll(anyList());
    }

    @Test
    void should_returnZeroAndSaveNothingMeaningful_when_noGuestOrdersToClaim() {
        when(orderRepository.findByGuestEmailAndGuestOrderTrue("none@example.com"))
            .thenReturn(new ArrayList<>());

        int claimed = orderService.claimGuestOrders("none@example.com", "auth0|real-user");

        assertEquals(0, claimed);
    }
}
