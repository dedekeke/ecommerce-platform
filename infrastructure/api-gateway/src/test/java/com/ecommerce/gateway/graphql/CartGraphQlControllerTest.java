package com.ecommerce.gateway.graphql;

import com.ecommerce.gateway.graphql.client.CartBackendClient;
import com.ecommerce.gateway.graphql.client.ProductBackendClient;
import com.ecommerce.gateway.graphql.controller.CartGraphQlController;
import com.ecommerce.gateway.graphql.controller.SchemaAdapterController;
import com.ecommerce.gateway.graphql.dataloader.ProductDataLoaderRegistrar;
import com.ecommerce.gateway.graphql.dto.CartDto;
import com.ecommerce.gateway.graphql.dto.CartItemDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import com.ecommerce.gateway.graphql.dto.PromotionDto;
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
import static org.mockito.Mockito.when;

/**
 * Verifies cart query auth surface and the {@code CartItem.product} nested
 * resolver path.
 */
@GraphQlTest(controllers = {CartGraphQlController.class, SchemaAdapterController.class})
@Import({ProductDataLoaderRegistrar.class, com.ecommerce.gateway.graphql.config.GraphQlSecurityConfig.class})
class CartGraphQlControllerTest {

    @Autowired
    private ExecutionGraphQlService graphQlService;

    @MockBean
    private CartBackendClient cartClient;

    @MockBean
    private ProductBackendClient productClient;

    private GraphQlTester tester;

    @BeforeEach
    void setUp() {
        tester = ExecutionGraphQlServiceTester.create(graphQlService);
    }

    @Test
    @WithMockUser(username = "alice")
    void should_returnCart_when_authenticatedUserQueriesOwnCart() {
        CartDto c = new CartDto(
                "alice",
                List.of(new CartItemDto("p1", 2, new BigDecimal("4.50"), new BigDecimal("9.00"))),
                new BigDecimal("9.00"),
                2,
                List.of(new PromotionDto("SAVE10", "10% off", new BigDecimal("0.90")))
        );
        when(cartClient.getCart("alice")).thenReturn(Mono.just(c));
        when(productClient.findByIds(any())).thenReturn(Mono.just(java.util.Map.of(
                "p1", new ProductDto("p1", "Prod1", null, new BigDecimal("4.50"), "USD",
                        List.of(), null, 5, true)
        )));

        tester.document("""
                query {
                  cart(userId:"alice") {
                    userId
                    subtotal
                    itemCount
                    appliedPromotions { code description discountAmount }
                    items {
                      productId
                      quantity
                      unitPrice
                      subtotal
                      product { id name }
                    }
                  }
                }
                """)
                .execute()
                .path("cart.userId").entity(String.class).isEqualTo("alice")
                .path("cart.subtotal").entity(Double.class).isEqualTo(9.00)
                .path("cart.itemCount").entity(Integer.class).isEqualTo(2)
                .path("cart.appliedPromotions[0].code").entity(String.class).isEqualTo("SAVE10")
                .path("cart.items[0].productId").entity(String.class).isEqualTo("p1")
                .path("cart.items[0].unitPrice").entity(Double.class).isEqualTo(4.50)
                .path("cart.items[0].product.name").entity(String.class).isEqualTo("Prod1");
    }

    @Test
    @WithAnonymousUser
    void should_rejectQuery_when_anonymousUserQueriesCart() {
        // Anonymous principals must not pass @PreAuthorize("isAuthenticated()").
        // The GraphQL response carries a top-level error and a null data field.
        tester.document("query { cart(userId:\"alice\") { userId } }")
                .execute()
                .errors()
                .expect(err -> err.getErrorType() != null);
    }
}
