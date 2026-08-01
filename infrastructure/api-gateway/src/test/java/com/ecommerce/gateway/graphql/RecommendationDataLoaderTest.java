package com.ecommerce.gateway.graphql;

import com.ecommerce.gateway.graphql.client.OrderBackendClient;
import com.ecommerce.gateway.graphql.client.ProductBackendClient;
import com.ecommerce.gateway.graphql.controller.OrderGraphQlController;
import com.ecommerce.gateway.graphql.controller.SchemaAdapterController;
import com.ecommerce.gateway.graphql.dataloader.ProductDataLoaderRegistrar;
import com.ecommerce.gateway.graphql.dto.OrderDto;
import com.ecommerce.gateway.graphql.dto.OrderItemDto;
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
import org.springframework.security.test.context.support.WithMockUser;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Behavioural test that proves the DataLoader collapses N product fetches
 * into a single batched call.
 *
 * <p>Scenario: {@code order { items { product { name } } }} with 5 line items.
 * Without batching this is 5 round-trips to product-service. With the
 * {@code productById} DataLoader registered against the
 * {@link org.springframework.graphql.execution.BatchLoaderRegistry} we expect
 * exactly <b>one</b> call into {@code ProductBackendClient.findByIds(List)}.
 */
@GraphQlTest(controllers = {OrderGraphQlController.class, SchemaAdapterController.class})
@Import({ProductDataLoaderRegistrar.class, com.ecommerce.gateway.graphql.config.GraphQlSecurityConfig.class})
class RecommendationDataLoaderTest {

    @Autowired
    private ExecutionGraphQlService graphQlService;

    @MockBean
    private OrderBackendClient orderClient;

    @MockBean
    private ProductBackendClient productClient;

    private GraphQlTester tester;
    private final AtomicInteger findByIdsCalls = new AtomicInteger();
    private final AtomicInteger findByIdCalls = new AtomicInteger();

    @BeforeEach
    void setUp() {
        tester = ExecutionGraphQlServiceTester.create(graphQlService);

        OrderDto order = new OrderDto(
                "ord-1", "ORD-001", "alice", "PAID", new BigDecimal("100.00"),
                List.of(
                        new OrderItemDto("p1", 1, new BigDecimal("10")),
                        new OrderItemDto("p2", 1, new BigDecimal("20")),
                        new OrderItemDto("p3", 1, new BigDecimal("30")),
                        new OrderItemDto("p4", 1, new BigDecimal("40")),
                        new OrderItemDto("p5", 1, new BigDecimal("50"))
                ),
                "2026-04-29T10:00:00Z"
        );
        when(orderClient.findById("ord-1", "ord-1")).thenReturn(Mono.just(order));

        // Spy on the count of *batched* calls; the DataLoader contract is that
        // findByIds() is invoked once per dispatch cycle, regardless of how
        // many keys were queued.
        when(productClient.findByIds(anyList())).thenAnswer(inv -> {
            findByIdsCalls.incrementAndGet();
            List<String> keys = inv.getArgument(0);
            Map<String, ProductDto> result = new java.util.HashMap<>();
            for (String k : keys) {
                result.put(k, new ProductDto(k, "Name-" + k, null, new BigDecimal("1"),
                        "USD", List.of(), null, 1, true));
            }
            return Mono.just(result);
        });

        // Also count direct findById to assert it is NOT used by the nested resolver.
        when(productClient.findById(org.mockito.ArgumentMatchers.anyString())).thenAnswer(inv -> {
            findByIdCalls.incrementAndGet();
            return Mono.empty();
        });
    }

    @Test
    @WithMockUser(username = "alice")
    void should_batchAllProductLookups_when_orderHasFiveItems() {
        tester.document("""
                query {
                  order(id:"ord-1") {
                    id
                    items {
                      productId
                      product { id name }
                    }
                  }
                }
                """)
                .execute()
                .path("order.items").entityList(Object.class).hasSize(5);

        // Five OrderItem.product nested resolutions -> ONE batch call.
        assertThat(findByIdsCalls.get())
                .as("DataLoader should batch 5 product lookups into a single call")
                .isEqualTo(1);
        assertThat(findByIdCalls.get())
                .as("Nested resolver must not bypass DataLoader with per-id calls")
                .isEqualTo(0);
    }
}
