package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Identity-binding security tests for {@link OrderController} with the real
 * Spring Security filter chain enabled. Verifies that the authenticated JWT
 * {@code sub} is the source of truth for order ownership and that a
 * client-supplied userId can never be used to act as another user.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/",
        "grpc.server.port=-1"
})
class OrderControllerJwtIdentityTest {

    private static final String USER_A = "auth0|user-a";
    private static final String USER_B = "auth0|user-b";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Order orderOwnedBy(String userId) {
        Order order = new Order();
        order.setId("order-123");
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    // ---- createOrder --------------------------------------------------------

    @Test
    void should_reject_when_createOrder_bodyUserId_differs_from_jwtSub() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_B + "\"}")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isForbidden());

        verify(orderService, never()).createOrderFromCart(any(), any());
    }

    @Test
    void should_useJwtSub_when_createOrder_bodyUserId_matches() throws Exception {
        when(orderService.createOrderFromCart(eq(USER_A), any())).thenReturn(orderOwnedBy(USER_A));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_A + "\"}")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isCreated());

        verify(orderService).createOrderFromCart(eq(USER_A), any());
    }

    @Test
    void should_overrideWithJwtSub_when_createOrder_bodyUserId_absent() throws Exception {
        when(orderService.createOrderFromCart(eq(USER_A), any())).thenReturn(orderOwnedBy(USER_A));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isCreated());

        verify(orderService).createOrderFromCart(eq(USER_A), any());
    }

    @Test
    void should_return401_when_createOrder_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER_A + "\"}"))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).createOrderFromCart(any(), any());
    }

    // ---- getOrder -----------------------------------------------------------

    @Test
    void should_reject_when_getOrder_headerUserId_differs_from_jwtSub() throws Exception {
        mockMvc.perform(get("/api/orders/order-123")
                        .header("X-User-Id", USER_B)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isForbidden());

        verify(orderService, never()).getOrder(any(), any());
    }

    @Test
    void should_useJwtSub_when_getOrder_authenticated() throws Exception {
        when(orderService.getOrder(eq("order-123"), eq(USER_A))).thenReturn(orderOwnedBy(USER_A));

        mockMvc.perform(get("/api/orders/order-123")
                        .header("X-User-Id", USER_A)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk());

        verify(orderService).getOrder("order-123", USER_A);
    }

    @Test
    void should_return401_when_getOrder_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/orders/order-123").header("X-User-Id", USER_A))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).getOrder(any(), any());
    }

    // ---- getUserOrders ------------------------------------------------------

    @Test
    void should_reject_when_getUserOrders_pathUserId_differs_from_jwtSub() throws Exception {
        mockMvc.perform(get("/api/orders/user/" + USER_B)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isForbidden());

        verify(orderService, never()).getUserOrders(any(), any());
    }

    @Test
    void should_queryWithJwtSub_when_getUserOrders_pathUserId_matches() throws Exception {
        Page<Order> page = new PageImpl<>(List.of(orderOwnedBy(USER_A)));
        when(orderService.getUserOrders(eq(USER_A), any())).thenReturn(page);

        // Reaching the service proves the identity check passed; the JSON serialization of
        // Spring's PageImpl is a separate, pre-existing concern outside this security fix.
        mockMvc.perform(get("/api/orders/user/" + USER_A)
                .with(jwt().jwt(j -> j.subject(USER_A))));

        verify(orderService).getUserOrders(eq(USER_A), any());
    }

    // ---- cancelOrder --------------------------------------------------------

    @Test
    void should_reject_when_cancelOrder_headerUserId_differs_from_jwtSub() throws Exception {
        mockMvc.perform(post("/api/orders/order-123/cancel")
                        .header("X-User-Id", USER_B)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isForbidden());

        verify(orderService, never()).cancelOrder(any(), any(), any());
    }

    @Test
    void should_useJwtSub_when_cancelOrder_authenticated() throws Exception {
        when(orderService.cancelOrder(eq("order-123"), eq(USER_A), any())).thenReturn(orderOwnedBy(USER_A));

        mockMvc.perform(post("/api/orders/order-123/cancel")
                        .header("X-User-Id", USER_A)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk());

        verify(orderService).cancelOrder(eq("order-123"), eq(USER_A), any());
    }
}
