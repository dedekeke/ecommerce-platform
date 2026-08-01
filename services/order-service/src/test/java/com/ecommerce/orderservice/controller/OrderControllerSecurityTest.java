package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for OrderController.
 *
 * Boots the real Spring Security filter chain ({@code security.enabled=true}) so
 * the JWT-scope authorization on the status-update endpoint is exercised end to
 * end: unauthenticated callers get 401, authenticated callers without
 * {@code SCOPE_admin} get 403, and admin-scoped callers succeed. The JwtDecoder
 * is mocked so the resource server starts without contacting Auth0; the
 * authenticated principal is supplied via the {@code jwt()} post-processor.
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
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void updateOrderStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(
                put("/api/orders/order-123/status")
                    .param("status", "SHIPPED"))
            .andExpect(status().isUnauthorized());

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void updateOrderStatus_authenticatedWithoutAdminScope_returns403() throws Exception {
        mockMvc.perform(
                put("/api/orders/order-123/status")
                    .param("status", "SHIPPED")
                    .with(jwt().jwt(jwt -> jwt.claim("scope", "read:orders"))))
            .andExpect(status().isForbidden());

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    void updateOrderStatus_authenticatedWithAdminScope_returns200() throws Exception {
        Order updated = new Order();
        updated.setId("order-123");
        updated.setStatus(OrderStatus.SHIPPED);
        when(orderService.updateOrderStatus(eq("order-123"), eq(OrderStatus.SHIPPED)))
            .thenReturn(updated);

        mockMvc.perform(
                put("/api/orders/order-123/status")
                    .param("status", "SHIPPED")
                    .with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_admin"))))
            .andExpect(status().isOk());

        verify(orderService).updateOrderStatus("order-123", OrderStatus.SHIPPED);
    }
}
