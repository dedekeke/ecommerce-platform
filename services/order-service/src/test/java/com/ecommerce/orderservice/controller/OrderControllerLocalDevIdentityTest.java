package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Local-dev fallback tests for {@link OrderController} with {@code security.enabled=false}.
 * Mirrors payment-service behaviour: when no JWT principal is present the
 * client-supplied userId (body / header / path) is honoured so local development
 * against a security-disabled service keeps working.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=false",
        "grpc.server.port=-1"
})
class OrderControllerLocalDevIdentityTest {

    private static final String USER_A = "local-user-a";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    private static Order orderOwnedBy(String userId) {
        Order order = new Order();
        order.setId("order-123");
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    @Test
    void should_useBodyUserId_when_createOrder_andSecurityDisabled() throws Exception {
        when(orderService.createOrderFromCart(eq(USER_A), any())).thenReturn(orderOwnedBy(USER_A));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_A + "\"}"))
                .andExpect(status().isCreated());

        verify(orderService).createOrderFromCart(eq(USER_A), any());
    }

    @Test
    void should_useHeaderUserId_when_getOrder_andSecurityDisabled() throws Exception {
        when(orderService.getOrder(eq("order-123"), eq(USER_A))).thenReturn(orderOwnedBy(USER_A));

        mockMvc.perform(get("/api/orders/order-123").header("X-User-Id", USER_A))
                .andExpect(status().isOk());

        verify(orderService).getOrder("order-123", USER_A);
    }

    @Test
    void should_usePathUserId_when_getUserOrders_andSecurityDisabled() throws Exception {
        when(orderService.getUserOrders(eq(USER_A), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        // Reaching the service proves the client-supplied path userId is honoured in local dev.
        mockMvc.perform(get("/api/orders/user/" + USER_A));

        verify(orderService).getUserOrders(eq(USER_A), any());
    }

    @Test
    void should_useHeaderUserId_when_cancelOrder_andSecurityDisabled() throws Exception {
        when(orderService.cancelOrder(eq("order-123"), eq(USER_A), any())).thenReturn(orderOwnedBy(USER_A));

        mockMvc.perform(post("/api/orders/order-123/cancel").header("X-User-Id", USER_A))
                .andExpect(status().isOk());

        verify(orderService).cancelOrder(eq("order-123"), eq(USER_A), any());
    }
}
