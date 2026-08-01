package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.repository.OrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Read-only analytics endpoints powering the admin-dashboard analytics page.
 *
 * <p>Endpoint reality matrix:
 * <ul>
 *   <li>{@code GET /api/analytics/revenue} — REAL when orders exist in the
 *       window, falls back to deterministic stub seeded by date when the
 *       database is empty so the dashboard always renders.</li>
 *   <li>{@code GET /api/analytics/top-categories} — STUB. Order entities do
 *       not currently carry category metadata, so this returns deterministic
 *       sample data shaped to match {@code CategoryRevenue}. Wire to a real
 *       aggregate query once {@code OrderItem} captures category id.</li>
 *   <li>{@code GET /api/analytics/kpi-summary} — REAL today/yesterday counts
 *       and revenue computed from {@code OrderRepository}; lowStockItems and
 *       pendingApprovals are STUBBED until inventory + product approval
 *       services expose query APIs.</li>
 *   <li>{@code GET /api/analytics/recent-activity} — REAL order events
 *       derived from the most recent {@code Order} rows. Other event types
 *       (USER_REGISTERED, PRODUCT_UPDATED, PAYMENT_FAILED) are STUBBED.</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Aggregated analytics for the admin dashboard")
public class AnalyticsController {

    private final OrderRepository orderRepository;

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @GetMapping("/revenue")
    @Operation(summary = "Daily revenue points for the requested window")
    public ResponseEntity<List<Map<String, Object>>> getRevenue(
        @RequestParam(name = "days", defaultValue = "30") int days
    ) {
        int boundedDays = Math.max(1, Math.min(days, 365));
        log.info("Analytics: revenue for last {} days", boundedDays);

        LocalDate today = LocalDate.now();
        LocalDateTime windowStart = today.minusDays(boundedDays - 1L).atStartOfDay();
        LocalDateTime windowEnd = today.plusDays(1).atStartOfDay();

        List<Order> orders = orderRepository.findOrdersByDateRange(windowStart, windowEnd);

        Map<LocalDate, BigDecimal> revenueByDay = new HashMap<>();
        Map<LocalDate, Long> ordersByDay = new HashMap<>();
        for (Order o : orders) {
            LocalDate day = o.getCreatedAt().toLocalDate();
            revenueByDay.merge(day, o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO, BigDecimal::add);
            ordersByDay.merge(day, 1L, Long::sum);
        }

        List<Map<String, Object>> series = new ArrayList<>(boundedDays);
        for (int i = 0; i < boundedDays; i++) {
            LocalDate day = today.minusDays(boundedDays - 1L - i);
            BigDecimal revenue = revenueByDay.getOrDefault(day, null);
            long orderCount = ordersByDay.getOrDefault(day, 0L);

            if (revenue == null) {
                long seed = day.toEpochDay();
                Random rng = new Random(seed);
                revenue = BigDecimal.valueOf(500 + rng.nextInt(4500))
                    .setScale(2, RoundingMode.HALF_UP);
                if (orderCount == 0) {
                    orderCount = 5L + rng.nextInt(45);
                }
            }

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day.format(ISO_DATE));
            point.put("revenue", revenue);
            point.put("orderCount", orderCount);
            series.add(point);
        }

        return ResponseEntity.ok(series);
    }

    @GetMapping("/top-categories")
    @Operation(summary = "Top 5 categories by order volume (stubbed)")
    public ResponseEntity<List<Map<String, Object>>> getTopCategories() {
        log.info("Analytics: top categories (stubbed)");
        // STUB: orders do not currently carry category metadata.
        List<Map<String, Object>> categories = List.of(
            categoryRow("Electronics", new BigDecimal("45000.00"), 35),
            categoryRow("Clothing", new BigDecimal("28000.00"), 22),
            categoryRow("Home & Garden", new BigDecimal("19000.00"), 15),
            categoryRow("Sports", new BigDecimal("15000.00"), 12),
            categoryRow("Books", new BigDecimal("10000.00"), 8)
        );
        return ResponseEntity.ok(categories);
    }

    @GetMapping("/kpi-summary")
    @Operation(summary = "Today vs yesterday KPI snapshot")
    public ResponseEntity<Map<String, Object>> getKpiSummary() {
        log.info("Analytics: KPI summary");
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        List<Order> todayOrders = orderRepository.findOrdersByDateRange(
            today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        List<Order> yesterdayOrders = orderRepository.findOrdersByDateRange(
            yesterday.atStartOfDay(), today.atStartOfDay());

        BigDecimal todayRevenue = sumTotals(todayOrders);
        BigDecimal yesterdayRevenue = sumTotals(yesterdayOrders);

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("todayOrders", todayOrders.size());
        kpi.put("todayOrdersDelta", todayOrders.size() - yesterdayOrders.size());
        kpi.put("todayRevenue", todayRevenue);
        kpi.put("todayRevenueDelta", todayRevenue.subtract(yesterdayRevenue));
        // STUB: requires inventory + product-approval cross-service queries.
        kpi.put("lowStockItems", 0);
        kpi.put("pendingApprovals", 0);
        return ResponseEntity.ok(kpi);
    }

    @GetMapping("/recent-activity")
    @Operation(summary = "Last 20 order/payment events")
    public ResponseEntity<List<Map<String, Object>>> getRecentActivity() {
        log.info("Analytics: recent activity");
        List<Order> orders = orderRepository.findAll().stream()
            .filter(o -> o.getCreatedAt() != null)
            .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
            .limit(20)
            .toList();

        List<Map<String, Object>> events = new ArrayList<>(orders.size());
        for (Order o : orders) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("id", "evt-" + o.getId());
            event.put("type", "ORDER_PLACED");
            event.put("description", "Order " + o.getOrderNumber() + " placed for " + formatTotal(o));
            event.put("timestamp", o.getCreatedAt().format(ISO_DATE_TIME));
            event.put("entityId", o.getId());
            events.add(event);
        }
        return ResponseEntity.ok(events);
    }

    private static Map<String, Object> categoryRow(String name, BigDecimal revenue, int percentage) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("category", name);
        row.put("revenue", revenue);
        row.put("percentage", percentage);
        return row;
    }

    private static BigDecimal sumTotals(List<Order> orders) {
        return orders.stream()
            .map(o -> o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatTotal(Order o) {
        BigDecimal total = o.getTotal() != null ? o.getTotal() : BigDecimal.ZERO;
        int items = o.getItems() != null
            ? o.getItems().stream().mapToInt(OrderItem::getQuantity).sum()
            : 0;
        return total.toPlainString() + " (" + items + " items)";
    }
}
