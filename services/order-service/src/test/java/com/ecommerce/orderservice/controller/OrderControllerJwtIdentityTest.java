package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.dto.CheckoutResponse;
import com.ecommerce.orderservice.service.CheckoutService;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
    private CheckoutService checkoutService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Order orderOwnedBy(String userId) {
        Order order = new Order();
        order.setId("order-123");
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    /** Valid checkout body; the cart is fetched server-side so only the address is required. */
    private static String checkoutBody(String userId) {
        String userIdField = userId == null ? "" : "\"userId\":\"" + userId + "\",";
        return "{" + userIdField + "\"shippingAddress\":{"
            + "\"street\":\"1 Main St\",\"city\":\"SF\",\"state\":\"CA\","
            + "\"postalCode\":\"94105\",\"country\":\"USA\"}}";
    }

    private static CheckoutService.Outcome checkoutOutcome(String userId) {
        CheckoutResponse response = CheckoutResponse.fromExistingOrder(orderOwnedBy(userId), "USD");
        return new CheckoutService.Outcome(response, false);
    }

    // ---- createOrder --------------------------------------------------------

    @Test
    void should_reject_when_createOrder_bodyUserId_differs_from_jwtSub() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(USER_B))
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isForbidden());

        verify(checkoutService, never()).checkout(any(), any(), any());
    }

    @Test
    void should_useJwtSub_when_createOrder_bodyUserId_matches() throws Exception {
        when(checkoutService.checkout(eq(USER_A), any(), any())).thenReturn(checkoutOutcome(USER_A));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(USER_A))
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isCreated());

        verify(checkoutService).checkout(eq(USER_A), any(), any());
    }

    @Test
    void should_overrideWithJwtSub_when_createOrder_bodyUserId_absent() throws Exception {
        when(checkoutService.checkout(eq(USER_A), any(), any())).thenReturn(checkoutOutcome(USER_A));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(null))
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isCreated());

        verify(checkoutService).checkout(eq(USER_A), any(), any());
    }

    @Test
    void should_return401_when_createOrder_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(USER_A)))
                .andExpect(status().isUnauthorized());

        verify(checkoutService, never()).checkout(any(), any(), any());
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

        mockMvc.perform(get("/api/orders/user/" + USER_A)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk());

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

    // ---- admin acting on behalf of another user ----------------------------

    @Test
    void should_allowAdmin_when_createOrder_bodyUserId_differsFromAdminSub() throws Exception {
        when(checkoutService.checkout(eq(USER_B), any(), any())).thenReturn(checkoutOutcome(USER_B));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(checkoutBody(USER_B))
                        .with(jwt().jwt(j -> j.subject("auth0|admin"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isCreated());

        verify(checkoutService).checkout(eq(USER_B), any(), any());
    }

    @Test
    void should_allowAdmin_when_getUserOrders_pathUserId_differsFromAdminSub() throws Exception {
        Page<Order> page = new PageImpl<>(List.of(orderOwnedBy(USER_B)));
        when(orderService.getUserOrders(eq(USER_B), any())).thenReturn(page);

        mockMvc.perform(get("/api/orders/user/" + USER_B)
                        .with(jwt().jwt(j -> j.subject("auth0|admin"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk());

        verify(orderService).getUserOrders(eq(USER_B), any());
    }

    // ---- getOrderByNumber (IDOR) -------------------------------------------

    @Test
    void should_return200_when_getOrderByNumber_ownedByCaller() throws Exception {
        when(orderService.getOrderByNumber("ORD-1")).thenReturn(orderOwnedBy(USER_A));

        mockMvc.perform(get("/api/orders/number/ORD-1")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk());
    }

    @Test
    void should_return403_when_getOrderByNumber_ownedByAnotherUser() throws Exception {
        when(orderService.getOrderByNumber("ORD-1")).thenReturn(orderOwnedBy(USER_B));

        mockMvc.perform(get("/api/orders/number/ORD-1")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_allowAdmin_when_getOrderByNumber_ownedByAnotherUser() throws Exception {
        when(orderService.getOrderByNumber("ORD-1")).thenReturn(orderOwnedBy(USER_B));

        mockMvc.perform(get("/api/orders/number/ORD-1")
                        .with(jwt().jwt(j -> j.subject("auth0|admin"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void should_return401_when_getOrderByNumber_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/orders/number/ORD-1"))
                .andExpect(status().isUnauthorized());

        verify(orderService, never()).getOrderByNumber(any());
    }
}
