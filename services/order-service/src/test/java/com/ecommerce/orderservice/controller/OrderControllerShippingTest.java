package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.exception.InvalidOrderStatusTransitionException;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security + contract tests for the admin mark-shipped / mark-delivered
 * endpoints. Boots the real Spring Security filter chain so the
 * {@code SCOPE_admin} authorization is exercised end to end, and verifies the
 * response carries the tracking fields and that an invalid transition surfaces
 * as 409.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/",
        "grpc.server.port=-1"
})
class OrderControllerShippingTest {

    private static final String SHIP_BODY = "{\"carrier\":\"UPS\",\"trackingNumber\":\"1Z999AA10123456784\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static SimpleGrantedAuthority admin() {
        return new SimpleGrantedAuthority("SCOPE_admin");
    }

    @Test
    void markShipped_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/orders/order-123/mark-shipped")
                        .contentType(MediaType.APPLICATION_JSON).content(SHIP_BODY))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).markOrderShipped(any(), any(), any());
    }

    @Test
    void markShipped_withoutAdminScope_returns403() throws Exception {
        mockMvc.perform(post("/api/orders/order-123/mark-shipped")
                        .contentType(MediaType.APPLICATION_JSON).content(SHIP_BODY)
                        .with(jwt().jwt(j -> j.claim("scope", "read:orders"))))
                .andExpect(status().isForbidden());

        verify(orderService, never()).markOrderShipped(any(), any(), any());
    }

    @Test
    void markShipped_withAdminScope_returns200WithTracking() throws Exception {
        Order shipped = Order.builder()
                .id("order-123").orderNumber("ORD-1").userId("u1")
                .status(OrderStatus.SHIPPED)
                .carrier("UPS").trackingNumber("1Z999AA10123456784")
                .shippedAt(LocalDateTime.now())
                .build();
        when(orderService.markOrderShipped(eq("order-123"), eq("UPS"), eq("1Z999AA10123456784")))
                .thenReturn(shipped);

        mockMvc.perform(post("/api/orders/order-123/mark-shipped")
                        .contentType(MediaType.APPLICATION_JSON).content(SHIP_BODY)
                        .with(jwt().authorities(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"))
                .andExpect(jsonPath("$.carrier").value("UPS"))
                .andExpect(jsonPath("$.trackingNumber").value("1Z999AA10123456784"))
                .andExpect(jsonPath("$.shippedAt").exists());

        verify(orderService).markOrderShipped("order-123", "UPS", "1Z999AA10123456784");
    }

    @Test
    void markShipped_blankCarrier_returns400() throws Exception {
        mockMvc.perform(post("/api/orders/order-123/mark-shipped")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"\",\"trackingNumber\":\"1Z999\"}")
                        .with(jwt().authorities(admin())))
                .andExpect(status().isBadRequest());

        verify(orderService, never()).markOrderShipped(any(), any(), any());
    }

    @Test
    void markShipped_invalidTransition_returns409() throws Exception {
        when(orderService.markOrderShipped(eq("order-123"), any(), any()))
                .thenThrow(new InvalidOrderStatusTransitionException(
                        OrderStatus.PENDING, OrderStatus.SHIPPED));

        mockMvc.perform(post("/api/orders/order-123/mark-shipped")
                        .contentType(MediaType.APPLICATION_JSON).content(SHIP_BODY)
                        .with(jwt().authorities(admin())))
                .andExpect(status().isConflict());
    }

    @Test
    void markDelivered_withAdminScope_returns200() throws Exception {
        Order delivered = Order.builder()
                .id("order-123").orderNumber("ORD-1").userId("u1")
                .status(OrderStatus.DELIVERED)
                .deliveredAt(LocalDateTime.now())
                .build();
        when(orderService.markOrderDelivered("order-123")).thenReturn(delivered);

        mockMvc.perform(post("/api/orders/order-123/mark-delivered")
                        .with(jwt().authorities(admin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.deliveredAt").exists());

        verify(orderService).markOrderDelivered("order-123");
    }

    @Test
    void markDelivered_withoutAdminScope_returns403() throws Exception {
        mockMvc.perform(post("/api/orders/order-123/mark-delivered")
                        .with(jwt().jwt(j -> j.claim("scope", "read:orders"))))
                .andExpect(status().isForbidden());

        verify(orderService, never()).markOrderDelivered(any());
    }
}
