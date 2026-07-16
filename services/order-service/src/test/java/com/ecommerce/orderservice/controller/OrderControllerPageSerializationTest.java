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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Serialization contract tests for {@link OrderController#getUserOrders}.
 *
 * <p>Regression coverage for the bug where the endpoint returned a raw Spring
 * Data {@code PageImpl}. Spring Data explicitly declares this JSON non-portable:
 * it leaks the internal {@code pageable}/{@code sort} structure, has no stable
 * contract, and hard-fails serialization (HTTP 500) on the Spring Boot 3.3+
 * upgrade path. The endpoint must emit a stable, documented paged envelope
 * exposing only the fields clients depend on.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://test-tenant.auth0.com/",
        "grpc.server.port=-1"
})
class OrderControllerPageSerializationTest {

    private static final String USER_A = "auth0|user-a";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static Order orderOwnedBy(String userId) {
        Order order = new Order();
        order.setId("order-123");
        order.setOrderNumber("ORD-1");
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    @Test
    void should_return200_withStablePageEnvelope_when_getUserOrders() throws Exception {
        Page<Order> page = new PageImpl<>(
                List.of(orderOwnedBy(USER_A)),
                PageRequest.of(0, 10),
                1);
        when(orderService.getUserOrders(eq(USER_A), any())).thenReturn(page);

        mockMvc.perform(get("/api/orders/user/" + USER_A)
                        .param("page", "0")
                        .param("size", "10")
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].orderId").value("order-123"))
                .andExpect(jsonPath("$.content[0].orderNumber").value("ORD-1"))
                // Ownership/PII fields must not leak through the paged read contract.
                .andExpect(jsonPath("$.content[0].userId").doesNotExist())
                .andExpect(jsonPath("$.content[0].id").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.number").value(0))
                // The unstable internal Page structure must not leak into the contract.
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
    }

    @Test
    void should_return200_withEmptyContent_when_userHasNoOrders() throws Exception {
        Page<Order> page = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(orderService.getUserOrders(eq(USER_A), any())).thenReturn(page);

        mockMvc.perform(get("/api/orders/user/" + USER_A)
                        .with(jwt().jwt(j -> j.subject(USER_A))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.empty").value(true));
    }
}
