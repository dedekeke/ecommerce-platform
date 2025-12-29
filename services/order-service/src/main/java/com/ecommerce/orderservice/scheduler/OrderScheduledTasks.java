package com.ecommerce.orderservice.scheduler;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.repository.OrderRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Component
@Slf4j
public class OrderScheduledTasks {

    private final OrderRepository orderRepository;
    private final MeterRegistry meterRegistry;
    private final Counter abandonedOrdersCounter;
    private final Timer processAbandonedTimer;
    private final Timer salesReportTimer;
    private final int abandonedHoursThreshold;
    private final AtomicReference<BigDecimal> dailyRevenue = new AtomicReference<>(BigDecimal.ZERO);
    private final AtomicReference<Long> dailyOrderCount = new AtomicReference<>(0L);

    public OrderScheduledTasks(
            OrderRepository orderRepository,
            MeterRegistry meterRegistry,
            @Value("${order.scheduled.abandoned-hours-threshold:24}") int abandonedHoursThreshold) {
        this.orderRepository = orderRepository;
        this.meterRegistry = meterRegistry;
        this.abandonedHoursThreshold = abandonedHoursThreshold;
        this.abandonedOrdersCounter = Counter.builder("order.abandoned.cancelled")
                .description("Number of abandoned orders cancelled")
                .register(meterRegistry);
        this.processAbandonedTimer = Timer.builder("order.job.process_abandoned.duration")
                .description("Duration of process abandoned orders job")
                .register(meterRegistry);
        this.salesReportTimer = Timer.builder("order.job.sales_report.duration")
                .description("Duration of daily sales report job")
                .register(meterRegistry);

        // Register gauges for daily metrics
        Gauge.builder("order.daily.revenue", dailyRevenue, ref -> ref.get().doubleValue())
                .description("Daily revenue from completed orders")
                .register(meterRegistry);
        Gauge.builder("order.daily.count", dailyOrderCount, AtomicReference::get)
                .description("Daily count of orders")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${order.scheduled.abandoned-orders-cron:0 0 4 * * ?}")
    public void processAbandonedOrders() {
        log.info("Starting scheduled task: Process abandoned orders (pending for {} hours)", abandonedHoursThreshold);

        processAbandonedTimer.record(() -> {
            try {
                LocalDateTime cutoffTime = LocalDateTime.now().minusHours(abandonedHoursThreshold);
                List<Order> abandonedOrders = orderRepository.findAbandonedOrders(
                        OrderStatus.PENDING, cutoffTime);

                if (!abandonedOrders.isEmpty()) {
                    log.info("Found {} abandoned orders to cancel", abandonedOrders.size());

                    abandonedOrders.forEach(order -> {
                        order.updateStatus(OrderStatus.CANCELLED);
                        log.debug("Cancelled abandoned order: {}", order.getOrderNumber());
                    });

                    orderRepository.saveAll(abandonedOrders);
                    abandonedOrdersCounter.increment(abandonedOrders.size());

                    log.info("Successfully cancelled {} abandoned orders", abandonedOrders.size());
                } else {
                    log.debug("No abandoned orders found");
                }

            } catch (Exception e) {
                log.error("Error during abandoned orders processing: {}", e.getMessage(), e);
            }
        });

        log.info("Completed scheduled task: Process abandoned orders");
    }

    @Scheduled(cron = "${order.scheduled.cleanup-old-orders-cron:0 0 3 * * ?}")
    public void cleanupCompletedOrdersData() {
        log.info("Starting scheduled task: Cleanup old orders data");

        // This job can be used to archive or cleanup old order data
        // For now, it's a placeholder for future implementation
        // - Archive orders older than X years
        // - Clean up orphaned order items
        // - Remove sensitive data from very old orders

        log.info("Completed scheduled task: Cleanup old orders data");
    }

    @Scheduled(cron = "${order.scheduled.daily-sales-report-cron:0 0 1 * * ?}")
    public void generateDailySalesReport() {
        log.info("Starting scheduled task: Generate daily sales report");

        salesReportTimer.record(() -> {
            try {
                // Get yesterday's date range
                LocalDate yesterday = LocalDate.now().minusDays(1);
                LocalDateTime startOfDay = yesterday.atStartOfDay();
                LocalDateTime endOfDay = yesterday.atTime(LocalTime.MAX);

                List<Order> orders = orderRepository.findOrdersByDateRange(startOfDay, endOfDay);

                if (orders.isEmpty()) {
                    log.info("No orders found for date: {}", yesterday);
                    dailyRevenue.set(BigDecimal.ZERO);
                    dailyOrderCount.set(0L);
                    return;
                }

                // Calculate metrics
                long totalOrders = orders.size();
                long completedOrders = orders.stream()
                        .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                        .count();
                long cancelledOrders = orders.stream()
                        .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                        .count();

                BigDecimal totalRevenue = orders.stream()
                        .filter(o -> o.getStatus() == OrderStatus.DELIVERED ||
                                     o.getStatus() == OrderStatus.SHIPPED ||
                                     o.getStatus() == OrderStatus.PROCESSING ||
                                     o.getStatus() == OrderStatus.CONFIRMED)
                        .map(Order::getTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal averageOrderValue = totalOrders > 0
                        ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                // Group by status
                Map<OrderStatus, Long> ordersByStatus = orders.stream()
                        .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));

                // Update metrics for monitoring
                dailyRevenue.set(totalRevenue);
                dailyOrderCount.set(totalOrders);

                // Log report
                log.info("=== Daily Sales Report for {} ===", yesterday);
                log.info("Total Orders: {}", totalOrders);
                log.info("Completed Orders: {}", completedOrders);
                log.info("Cancelled Orders: {}", cancelledOrders);
                log.info("Total Revenue: ${}", totalRevenue);
                log.info("Average Order Value: ${}", averageOrderValue);
                log.info("Orders by Status: {}", ordersByStatus);
                log.info("=================================");

            } catch (Exception e) {
                log.error("Error generating daily sales report: {}", e.getMessage(), e);
            }
        });

        log.info("Completed scheduled task: Generate daily sales report");
    }
}
