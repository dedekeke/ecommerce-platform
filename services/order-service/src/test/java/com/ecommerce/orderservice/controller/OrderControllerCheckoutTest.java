package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.dto.CheckoutResponse;
import com.ecommerce.orderservice.exception.ConcurrentCheckoutException;
import com.ecommerce.orderservice.saga.OrderCreationSaga.CheckoutResult;
import com.ecommerce.orderservice.saga.OrderCreationSaga.SagaException;
import com.ecommerce.orderservice.service.CheckoutService;
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

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the checkout endpoint {@code POST /api/orders}: response
 * shape (client secret), idempotency status codes and error mapping. Security
 * disabled so the body userId is honoured; identity binding is covered by
 * {@link OrderControllerJwtIdentityTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=false",
        "grpc.server.port=-1"
})
class OrderControllerCheckoutTest {

    private static final String USER = "local-user";
    private static final String BODY = "{\"userId\":\"" + USER + "\",\"shippingAddress\":{"
            + "\"street\":\"1 Main St\",\"city\":\"SF\",\"state\":\"CA\","
            + "\"postalCode\":\"94105\",\"country\":\"USA\"}}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckoutService checkoutService;

    @MockBean
    private OrderService orderService;

    private static Order order() {
        Order order = new Order();
        order.setId("order-1");
        order.setOrderNumber("ORD-2026-0001");
        order.setUserId(USER);
        order.setStatus(OrderStatus.PENDING);
        order.setSubtotal(new BigDecimal("59.98"));
        order.setTax(new BigDecimal("4.80"));
        order.setShippingCost(new BigDecimal("5.99"));
        order.setTotal(new BigDecimal("70.77"));
        order.setPaymentIntentId("pi_1");
        order.setPaymentClientSecret("pi_1_secret");
        return order;
    }

    @Test
    void should_return201WithClientSecret_when_freshCheckout() throws Exception {
        CheckoutResponse response = CheckoutResponse.from(
            new CheckoutResult(order(), "pi_1", "pi_1_secret", "USD"));
        when(checkoutService.checkout(eq(USER), any(), any()))
            .thenReturn(new CheckoutService.Outcome(response, false));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.clientSecret").value("pi_1_secret"))
                .andExpect(jsonPath("$.paymentIntentId").value("pi_1"))
                .andExpect(jsonPath("$.currency").value("USD"));

        verify(checkoutService).checkout(eq(USER), eq("key-1"), any());
    }

    @Test
    void should_return200_when_idempotentReplay() throws Exception {
        CheckoutResponse response = CheckoutResponse.fromExistingOrder(order(), "USD");
        when(checkoutService.checkout(eq(USER), any(), any()))
            .thenReturn(new CheckoutService.Outcome(response, true));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-1"))
                // Replay must re-serve the persisted client secret so the owning
                // session can still complete payment.
                .andExpect(jsonPath("$.clientSecret").value("pi_1_secret"));
    }

    @Test
    void should_return409_when_concurrentCheckoutInProgress() throws Exception {
        when(checkoutService.checkout(eq(USER), any(), any()))
            .thenThrow(new ConcurrentCheckoutException("already in progress"));

        mockMvc.perform(post("/api/orders")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void should_return502_when_sagaFails() throws Exception {
        when(checkoutService.checkout(eq(USER), any(), any()))
            .thenThrow(new SagaException("payment gateway down"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isBadGateway());
    }

    @Test
    void should_return400_when_shippingAddressMissing() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + USER + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_notLeakClientSecret_when_gettingOrderDetail() throws Exception {
        // The raw Order entity is serialized by GET order-detail; the persisted
        // Stripe secret must never appear there (@JsonIgnore) — it is exposed
        // ONLY via the checkout CheckoutResponse.
        when(orderService.getOrder("order-1", USER)).thenReturn(order());

        mockMvc.perform(get("/api/orders/order-1").header("X-User-Id", USER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentIntentId").value("pi_1"))
                .andExpect(jsonPath("$.paymentClientSecret").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }
}
