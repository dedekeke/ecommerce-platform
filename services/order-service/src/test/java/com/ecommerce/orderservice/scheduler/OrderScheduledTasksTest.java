package com.ecommerce.orderservice.scheduler;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Scheduled Tasks Tests")
class OrderScheduledTasksTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderScheduledTasks scheduledTasks;

    @Captor
    private ArgumentCaptor<LocalDateTime> dateTimeCaptor;

    @Captor
    private ArgumentCaptor<List<Order>> ordersCaptor;

    private static final int ABANDONED_HOURS_THRESHOLD = 24;

    @BeforeEach
    void setUp() {
        scheduledTasks = new OrderScheduledTasks(
                orderRepository,
                new SimpleMeterRegistry(),
                ABANDONED_HOURS_THRESHOLD
        );
    }

    private Order createOrder(String id, OrderStatus status) {
        return Order.builder()
                .id(id)
                .orderNumber("ORD-" + id)
                .userId("user-" + id)
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("Process Abandoned Orders Tests")
    class ProcessAbandonedOrdersTests {

        @Test
        @DisplayName("Should cancel abandoned orders when found")
        void shouldCancelAbandonedOrdersWhenFound() {
            // Given
            Order abandonedOrder1 = createOrder("1", OrderStatus.PENDING);
            Order abandonedOrder2 = createOrder("2", OrderStatus.PENDING);
            List<Order> abandonedOrders = Arrays.asList(abandonedOrder1, abandonedOrder2);

            when(orderRepository.findAbandonedOrders(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(abandonedOrders);
            when(orderRepository.saveAll(anyList())).thenReturn(abandonedOrders);

            // When
            scheduledTasks.processAbandonedOrders();

            // Then
            verify(orderRepository).findAbandonedOrders(eq(OrderStatus.PENDING), any(LocalDateTime.class));
            verify(orderRepository).saveAll(ordersCaptor.capture());

            List<Order> savedOrders = ordersCaptor.getValue();
            assertThat(savedOrders).hasSize(2);
            assertThat(savedOrders).allMatch(order -> order.getStatus() == OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("Should not save anything when no abandoned orders found")
        void shouldNotSaveWhenNoAbandonedOrdersFound() {
            // Given
            when(orderRepository.findAbandonedOrders(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            // When
            scheduledTasks.processAbandonedOrders();

            // Then
            verify(orderRepository).findAbandonedOrders(eq(OrderStatus.PENDING), any(LocalDateTime.class));
            verify(orderRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Should use correct threshold time for abandoned order detection")
        void shouldUseCorrectThresholdTime() {
            // Given
            when(orderRepository.findAbandonedOrders(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            LocalDateTime beforeExecution = LocalDateTime.now().minusHours(ABANDONED_HOURS_THRESHOLD);

            // When
            scheduledTasks.processAbandonedOrders();

            // Then
            verify(orderRepository).findAbandonedOrders(eq(OrderStatus.PENDING), dateTimeCaptor.capture());
            LocalDateTime capturedThreshold = dateTimeCaptor.getValue();

            // Threshold should be approximately 24 hours ago (within 1 minute tolerance)
            assertThat(capturedThreshold).isBetween(
                    beforeExecution.minusMinutes(1),
                    LocalDateTime.now().minusHours(ABANDONED_HOURS_THRESHOLD).plusMinutes(1)
            );
        }

        @Test
        @DisplayName("Should handle exception gracefully during processing")
        void shouldHandleExceptionGracefully() {
            // Given
            when(orderRepository.findAbandonedOrders(eq(OrderStatus.PENDING), any(LocalDateTime.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When - should not throw
            scheduledTasks.processAbandonedOrders();

            // Then
            verify(orderRepository).findAbandonedOrders(eq(OrderStatus.PENDING), any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("Cleanup Old Orders Tests")
    class CleanupOldOrdersTests {

        @Test
        @DisplayName("Should log message when checking for old orders to cleanup")
        void shouldCheckForOldOrders() {
            // When
            scheduledTasks.cleanupCompletedOrdersData();

            // Then - verify the method runs without errors
            // This is a placeholder for future implementation
        }
    }

    @Nested
    @DisplayName("Daily Sales Report Tests")
    class DailySalesReportTests {

        @Test
        @DisplayName("Should generate sales report when orders exist")
        void shouldGenerateSalesReportWhenOrdersExist() {
            // Given
            Order order1 = createOrder("1", OrderStatus.DELIVERED);
            order1.setTotal(java.math.BigDecimal.valueOf(100.00));
            Order order2 = createOrder("2", OrderStatus.CONFIRMED);
            order2.setTotal(java.math.BigDecimal.valueOf(50.00));
            List<Order> orders = Arrays.asList(order1, order2);

            when(orderRepository.findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(orders);

            // When
            scheduledTasks.generateDailySalesReport();

            // Then
            verify(orderRepository).findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should handle empty orders gracefully")
        void shouldHandleEmptyOrdersGracefully() {
            // Given
            when(orderRepository.findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(Collections.emptyList());

            // When
            scheduledTasks.generateDailySalesReport();

            // Then
            verify(orderRepository).findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("Should handle exception gracefully during report generation")
        void shouldHandleExceptionGracefully() {
            // Given
            when(orderRepository.findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenThrow(new RuntimeException("Database error"));

            // When - should not throw
            scheduledTasks.generateDailySalesReport();

            // Then
            verify(orderRepository).findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class));
        }
    }
}
