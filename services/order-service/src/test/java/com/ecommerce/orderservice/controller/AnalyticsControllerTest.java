package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private AnalyticsController controller;

    @Test
    void revenueEndpointReturnsRequestedNumberOfDailyPoints() {
        when(orderRepository.findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        ResponseEntity<List<Map<String, Object>>> response = controller.getRevenue(7);

        assertNotNull(response.getBody());
        assertEquals(7, response.getBody().size());
        Map<String, Object> first = response.getBody().get(0);
        assertTrue(first.containsKey("date"), "expected 'date' key");
        assertTrue(first.containsKey("revenue"), "expected 'revenue' key");
        assertTrue(first.containsKey("orderCount"), "expected 'orderCount' key");
    }

    @Test
    void revenueEndpointClampsExcessiveDayRequests() {
        when(orderRepository.findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        ResponseEntity<List<Map<String, Object>>> response = controller.getRevenue(10_000);

        assertNotNull(response.getBody());
        assertEquals(365, response.getBody().size(), "should clamp to 365");
    }

    @Test
    void topCategoriesEndpointReturnsExpectedShape() {
        ResponseEntity<List<Map<String, Object>>> response = controller.getTopCategories();

        assertNotNull(response.getBody());
        assertTrue(response.getBody().size() >= 5, "should return at least 5 categories");
        Map<String, Object> first = response.getBody().get(0);
        assertTrue(first.containsKey("category"));
        assertTrue(first.containsKey("revenue"));
        assertTrue(first.containsKey("percentage"));
    }

    @Test
    void kpiSummaryReturnsAllExpectedKeys() {
        when(orderRepository.findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(Collections.emptyList());

        ResponseEntity<Map<String, Object>> response = controller.getKpiSummary();

        assertNotNull(response.getBody());
        Map<String, Object> body = response.getBody();
        assertTrue(body.containsKey("todayOrders"));
        assertTrue(body.containsKey("todayOrdersDelta"));
        assertTrue(body.containsKey("todayRevenue"));
        assertTrue(body.containsKey("todayRevenueDelta"));
        assertTrue(body.containsKey("lowStockItems"));
        assertTrue(body.containsKey("pendingApprovals"));
    }

    @Test
    void kpiSummaryComputesDeltaFromTodayAndYesterday() {
        Order todayOrder = sampleOrder("ORD-T", LocalDateTime.now(), new BigDecimal("100.00"));
        Order yesterdayOrder = sampleOrder("ORD-Y", LocalDateTime.now().minusDays(1), new BigDecimal("60.00"));

        when(orderRepository.findOrdersByDateRange(any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of(todayOrder))
            .thenReturn(List.of(yesterdayOrder));

        Map<String, Object> body = controller.getKpiSummary().getBody();

        assertNotNull(body);
        assertEquals(1, body.get("todayOrders"));
        assertEquals(0, body.get("todayOrdersDelta"));
        assertEquals(new BigDecimal("100.00"), body.get("todayRevenue"));
        assertEquals(new BigDecimal("40.00"), body.get("todayRevenueDelta"));
    }

    @Test
    void recentActivityReturnsLatestOrdersAsEvents() {
        Order o1 = sampleOrder("ORD-001", LocalDateTime.now().minusHours(1), new BigDecimal("25.00"));
        Order o2 = sampleOrder("ORD-002", LocalDateTime.now().minusHours(2), new BigDecimal("75.00"));
        when(orderRepository.findAll()).thenReturn(List.of(o2, o1));

        ResponseEntity<List<Map<String, Object>>> response = controller.getRecentActivity();

        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        Map<String, Object> latest = response.getBody().get(0);
        assertEquals("ORDER_PLACED", latest.get("type"));
        assertTrue(latest.containsKey("id"));
        assertTrue(latest.containsKey("description"));
        assertTrue(latest.containsKey("timestamp"));
        assertTrue(latest.containsKey("entityId"));
        assertEquals(o1.getId(), latest.get("entityId"), "events should be most-recent first");
    }

    private Order sampleOrder(String orderNumber, LocalDateTime createdAt, BigDecimal total) {
        Order o = new Order();
        o.setId("id-" + orderNumber);
        o.setOrderNumber(orderNumber);
        o.setUserId("user-1");
        o.setSubtotal(total);
        o.setTax(BigDecimal.ZERO);
        o.setShippingCost(BigDecimal.ZERO);
        o.setTotal(total);
        o.setCreatedAt(createdAt);
        o.setUpdatedAt(createdAt);
        return o;
    }
}
