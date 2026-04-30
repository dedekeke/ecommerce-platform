package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests for OrderController.
 *
 * Verifies that status-update mutations are restricted to admin users and that
 * unauthenticated or under-privileged callers receive the correct denial status.
 */
@WebMvcTest(OrderController.class)
@ActiveProfiles("test")
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    // ---------------------------------------------------------------------------
    // PUT /api/orders/{orderId}/status
    // ---------------------------------------------------------------------------

    @Test
    void updateOrderStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(
                put("/api/orders/order-123/status")
                    .param("status", "SHIPPED"))
            .andExpect(status().isUnauthorized());

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateOrderStatus_authenticatedWithoutAdminScope_returns403() throws Exception {
        mockMvc.perform(
                put("/api/orders/order-123/status")
                    .param("status", "SHIPPED"))
            .andExpect(status().isForbidden());

        verify(orderService, never()).updateOrderStatus(any(), any());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_admin")
    void updateOrderStatus_authenticatedWithAdminScope_returns200() throws Exception {
        Order updated = new Order();
        updated.setId("order-123");
        updated.setStatus(OrderStatus.SHIPPED);
        when(orderService.updateOrderStatus(eq("order-123"), eq(OrderStatus.SHIPPED)))
            .thenReturn(updated);

        mockMvc.perform(
                put("/api/orders/order-123/status")
                    .param("status", "SHIPPED"))
            .andExpect(status().isOk());

        verify(orderService).updateOrderStatus("order-123", OrderStatus.SHIPPED);
    }
}
