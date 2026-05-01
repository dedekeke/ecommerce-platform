package com.ecommerce.gateway.graphql;

import com.ecommerce.gateway.graphql.client.OrderBackendClient;
import com.ecommerce.gateway.graphql.client.ProductBackendClient;
import com.ecommerce.gateway.graphql.controller.OrderGraphQlController;
import com.ecommerce.gateway.graphql.controller.SchemaAdapterController;
import com.ecommerce.gateway.graphql.dataloader.ProductDataLoaderRegistrar;
import com.ecommerce.gateway.graphql.dto.OrderDto;
import com.ecommerce.gateway.graphql.dto.OrderItemDto;
import com.ecommerce.gateway.graphql.dto.PageDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@GraphQlTest(controllers = {OrderGraphQlController.class, SchemaAdapterController.class})
@Import({ProductDataLoaderRegistrar.class, com.ecommerce.gateway.graphql.config.GraphQlSecurityConfig.class})
class OrderGraphQlControllerTest {

    @Autowired
    private ExecutionGraphQlService graphQlService;

    @MockBean
    private OrderBackendClient orderClient;

    @MockBean
    private ProductBackendClient productClient;

    private GraphQlTester tester;

    @BeforeEach
    void setUp() {
        tester = ExecutionGraphQlServiceTester.create(graphQlService);
    }

    @Test
    @WithMockUser
    void should_returnOrder_when_authenticated() {
        OrderDto o = new OrderDto("o1", "ORD-1", "u", "PAID", new BigDecimal("12.00"),
                List.of(new OrderItemDto("p1", 1, new BigDecimal("12"))), "2026-04-29T10:00:00Z");
        when(orderClient.findById(anyString(), anyString())).thenReturn(Mono.just(o));
        when(productClient.findByIds(any())).thenReturn(Mono.just(java.util.Map.of(
                "p1", new ProductDto("p1", "Prod1", null, new BigDecimal("12"), "USD",
                        List.of(), null, 1, true)
        )));

        tester.document("""
                query { order(id:"o1") {
                  id orderNumber status totalAmount
                  items { productId quantity unitPrice product { name } }
                } }
                """)
                .execute()
                .path("order.id").entity(String.class).isEqualTo("o1")
                .path("order.totalAmount").entity(Double.class).isEqualTo(12.00)
                .path("order.items[0].product.name").entity(String.class).isEqualTo("Prod1");
    }

    @Test
    @WithMockUser
    void should_returnPagedOrders_when_myOrdersQueried() {
        OrderDto o = new OrderDto("o1", "ORD-1", "u", "PAID", new BigDecimal("1.00"),
                List.of(), "2026-04-29T10:00:00Z");
        when(orderClient.findForUser(anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.just(new PageDto<>(List.of(o), 1L, 1, 0)));

        tester.document("""
                query { myOrders(userId:"u") {
                  totalElements pageNumber content { id }
                } }
                """)
                .execute()
                .path("myOrders.totalElements").entity(Long.class).isEqualTo(1L);
    }

    @Test
    @WithAnonymousUser
    void should_rejectQuery_when_anonymousAccessesOrder() {
        tester.document("query { order(id:\"o1\") { id } }")
                .execute()
                .errors()
                .expect(err -> err.getErrorType() != null);
    }
}
