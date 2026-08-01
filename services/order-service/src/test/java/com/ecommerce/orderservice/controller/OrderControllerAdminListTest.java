package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security + contract tests for the admin order list endpoint
 * ({@code GET /api/orders}).
 *
 * <p>Boots the real Spring Security filter chain ({@code security.enabled=true})
 * so the {@code @PreAuthorize("SCOPE_admin")} gate is exercised end to end:
 * unauthenticated -> 401, authenticated non-admin -> 403, admin -> 200. The 200
 * path additionally pins the admin contract: the response carries the customer
 * identity (userId / guestEmail) an admin needs, but never a payment secret.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        // Required by PromotionServiceClient's boot guard whenever security is on.
        "promotion.service.internal-token=test-internal-service-token",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/",
        "grpc.server.port=-1"
})
class OrderControllerAdminListTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Order sampleOrder() {
        OrderItem item = OrderItem.builder()
            .productId("p-1").productName("Widget")
            .price(new BigDecimal("50.00")).quantity(2)
            .subtotal(new BigDecimal("100.00"))
            .build();
        Order order = Order.builder()
            .id("order-1")
            .orderNumber("ORD-1")
            .userId("auth0|user-a")
            .status(OrderStatus.CONFIRMED)
            .subtotal(new BigDecimal("100.00"))
            .tax(new BigDecimal("8.00"))
            .shippingCost(new BigDecimal("5.00"))
            .total(new BigDecimal("113.00"))
            .paymentIntentId("pi_123")
            .paymentClientSecret("pi_123_secret_SHOULD_NOT_LEAK")
            .guestOrder(true)
            .guestEmail("guest@example.com")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .items(List.of(item))
            .build();
        item.setOrder(order);
        return order;
    }

    @Test
    void listOrders_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/orders"))
            .andExpect(status().isUnauthorized());

        verify(orderService, never()).getAllOrders(any(), any());
    }

    @Test
    void listOrders_authenticatedWithoutAdminScope_returns403() throws Exception {
        mockMvc.perform(get("/api/orders")
                .with(jwt().jwt(j -> j.claim("scope", "read:orders"))))
            .andExpect(status().isForbidden());

        verify(orderService, never()).getAllOrders(any(), any());
    }

    @Test
    void listOrders_admin_returns200_withCustomerIdentity_andNoSecrets() throws Exception {
        Page<Order> page = new PageImpl<>(List.of(sampleOrder()), PageRequest.of(0, 20), 1);
        when(orderService.getAllOrders(isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/orders")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].orderNumber").value("ORD-1"))
            .andExpect(jsonPath("$.content[0].userId").value("auth0|user-a"))
            .andExpect(jsonPath("$.content[0].guestEmail").value("guest@example.com"))
            .andExpect(jsonPath("$.content[0].guestOrder").value(true))
            .andExpect(jsonPath("$.content[0].itemCount").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("SHOULD_NOT_LEAK"))))
            .andExpect(jsonPath("$.content[0].paymentClientSecret").doesNotExist())
            .andExpect(jsonPath("$.content[0].paymentIntentId").doesNotExist());
    }

    @Test
    void listOrders_admin_withStatusFilter_passesStatusToService() throws Exception {
        Page<Order> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(orderService.getAllOrders(any(), any(Pageable.class))).thenReturn(empty);

        mockMvc.perform(get("/api/orders")
                .param("status", "SHIPPED")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.empty").value(true));

        ArgumentCaptor<OrderStatus> statusCaptor = ArgumentCaptor.forClass(OrderStatus.class);
        verify(orderService).getAllOrders(statusCaptor.capture(), any(Pageable.class));
        assertThat(statusCaptor.getValue()).isEqualTo(OrderStatus.SHIPPED);
    }
}
