package com.ecommerce.orderservice.controller;

import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.Order;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.domain.enums.OrderStatus;
import com.ecommerce.orderservice.service.CheckoutService;
import com.ecommerce.orderservice.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Response-shape contract tests for {@link OrderController}: the order-returning
 * endpoints must emit the client-safe {@link
 * com.ecommerce.orderservice.dto.OrderResponse} view and must never leak the
 * Stripe {@code paymentClientSecret} or ownership/PII entity columns.
 *
 * <p>Regression guard for the P2 debt: the raw JPA entity used to be serialized
 * directly, which leaked the client secret until a fragile {@code @JsonIgnore}
 * workaround was added.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/",
        "grpc.server.port=-1"
})
class OrderControllerResponseDtoTest {

    private static final String USER_A = "auth0|user-a";
    private static final String SECRET = "pi_123_secret_SHOULD_NOT_LEAK";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private CheckoutService checkoutService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Order fullOrder() {
        Order order = Order.builder()
            .id("order-123")
            .orderNumber("ORD-1")
            .userId(USER_A)
            .status(OrderStatus.CONFIRMED)
            .subtotal(new BigDecimal("100.00"))
            .tax(new BigDecimal("8.00"))
            .shippingCost(new BigDecimal("5.00"))
            .discountAmount(new BigDecimal("10.00"))
            .loyaltyDiscount(new BigDecimal("2.00"))
            .total(new BigDecimal("101.00"))
            .paymentIntentId("pi_123")
            .paymentClientSecret(SECRET)
            .guestOrder(false)
            .guestEmail("guest@example.com")
            .shippingAddress(Address.builder()
                .street("1 Main St").city("SF").state("CA")
                .postalCode("94105").country("USA").build())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
        order.setItems(List.of(OrderItem.builder()
            .productId("p-1").productName("Widget")
            .price(new BigDecimal("50.00")).quantity(2)
            .subtotal(new BigDecimal("100.00")).build()));
        return order;
    }

    @Test
    void should_returnClientSafeFields_when_getOrderById() throws Exception {
        when(orderService.getOrder(eq("order-123"), eq(USER_A))).thenReturn(fullOrder());

        mockMvc.perform(get("/api/orders/order-123")
                        .header("X-User-Id", USER_A)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.orderId").value("order-123"))
                .andExpect(jsonPath("$.orderNumber").value("ORD-1"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.total").value(101.00))
                .andExpect(jsonPath("$.paymentIntentId").value("pi_123"))
                .andExpect(jsonPath("$.guestOrder").value(false))
                .andExpect(jsonPath("$.items[0].productId").value("p-1"))
                .andExpect(jsonPath("$.shippingAddress.street").value("1 Main St"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void should_notLeakSecretOrPii_when_getOrderById() throws Exception {
        when(orderService.getOrder(eq("order-123"), eq(USER_A))).thenReturn(fullOrder());

        String body = mockMvc.perform(get("/api/orders/order-123")
                        .header("X-User-Id", USER_A)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentClientSecret").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist())
                .andExpect(jsonPath("$.userId").doesNotExist())
                .andExpect(jsonPath("$.guestEmail").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
            .doesNotContain(SECRET)
            .doesNotContain("guest@example.com");
    }

    @Test
    void should_returnClientSafeFields_when_getOrderByNumber() throws Exception {
        when(orderService.getOrderByNumber("ORD-1")).thenReturn(fullOrder());

        mockMvc.perform(get("/api/orders/number/ORD-1")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("order-123"))
                .andExpect(jsonPath("$.paymentClientSecret").doesNotExist())
                .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test
    void should_returnPageOfClientSafeOrders_when_getUserOrders() throws Exception {
        Page<Order> page = new PageImpl<>(List.of(fullOrder()), PageRequest.of(0, 10), 1);
        when(orderService.getUserOrders(eq(USER_A), any())).thenReturn(page);

        mockMvc.perform(get("/api/orders/user/" + USER_A)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].orderId").value("order-123"))
                .andExpect(jsonPath("$.content[0].total").value(101.00))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paymentClientSecret").doesNotExist())
                .andExpect(jsonPath("$.content[0].clientSecret").doesNotExist())
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[0].guestEmail").doesNotExist());
    }
}
