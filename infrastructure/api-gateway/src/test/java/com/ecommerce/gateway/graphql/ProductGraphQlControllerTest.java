package com.ecommerce.gateway.graphql;

import com.ecommerce.gateway.graphql.client.ProductBackendClient;
import com.ecommerce.gateway.graphql.client.RecommendationBackendClient;
import com.ecommerce.gateway.graphql.controller.ProductGraphQlController;
import com.ecommerce.gateway.graphql.controller.SchemaAdapterController;
import com.ecommerce.gateway.graphql.dataloader.ProductDataLoaderRegistrar;
import com.ecommerce.gateway.graphql.dto.CategoryDto;
import com.ecommerce.gateway.graphql.dto.PageDto;
import com.ecommerce.gateway.graphql.dto.ProductDto;
import com.ecommerce.gateway.graphql.dto.RecommendationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.graphql.test.tester.ExecutionGraphQlServiceTester;
import org.springframework.graphql.test.tester.GraphQlTester;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Slice tests for {@link ProductGraphQlController}.
 *
 * <p>{@link GraphQlTest} starts only the GraphQL machinery (schema, controllers,
 * data fetchers, DataLoaders) — no security, no WebFlux server. Backend
 * WebClient access is mocked through the {@code @MockBean}s.
 */
@GraphQlTest(controllers = {ProductGraphQlController.class, SchemaAdapterController.class})
@Import({ProductDataLoaderRegistrar.class})
class ProductGraphQlControllerTest {

    @Autowired
    private ExecutionGraphQlService graphQlService;

    @MockBean
    private ProductBackendClient productClient;

    @MockBean
    private RecommendationBackendClient recommendationClient;

    private GraphQlTester tester;

    @BeforeEach
    void setUp() {
        tester = ExecutionGraphQlServiceTester.create(graphQlService);
    }

    @Test
    void should_returnProduct_when_queryProductById() {
        ProductDto p = new ProductDto("42", "Widget", "A widget", new BigDecimal("9.99"),
                "USD", List.of("img1.png"), new CategoryDto("c1", "Cat", "cat"),
                5, true);
        when(productClient.findById("42")).thenReturn(Mono.just(p));

        tester.document("""
                query { product(id: "42") { id name price currency inStock liveStockQty
                  category { id name slug }
                  images
                } }
                """)
                .execute()
                .path("product.id").entity(String.class).isEqualTo("42")
                .path("product.name").entity(String.class).isEqualTo("Widget")
                .path("product.price").entity(Double.class).isEqualTo(9.99)
                .path("product.inStock").entity(Boolean.class).isEqualTo(true)
                .path("product.liveStockQty").entity(Integer.class).isEqualTo(5)
                .path("product.category.slug").entity(String.class).isEqualTo("cat")
                .path("product.images").entityList(String.class).hasSize(1);
    }

    @Test
    void should_returnNull_when_productNotFound() {
        when(productClient.findById("missing")).thenReturn(Mono.empty());

        tester.document("query { product(id: \"missing\") { id name } }")
                .execute()
                .path("product").valueIsNull();
    }

    @Test
    void should_paginateProducts_when_queryProducts() {
        ProductDto p = new ProductDto("1", "P1", null, new BigDecimal("1.0"), "USD",
                List.of(), null, 1, true);
        when(productClient.search(eq("term"), eq("books"), eq(0), eq(20)))
                .thenReturn(Mono.just(new PageDto<>(List.of(p), 1L, 1, 0)));

        tester.document("""
                query {
                  products(search: "term", category: "books") {
                    content { id name }
                    totalElements
                    pageNumber
                  }
                }
                """)
                .execute()
                .path("products.content").entityList(Object.class).hasSize(1)
                .path("products.totalElements").entity(Long.class).isEqualTo(1L)
                .path("products.pageNumber").entity(Integer.class).isEqualTo(0);
    }

    @Test
    void should_returnRecommendations_when_queryRecommendations() {
        when(recommendationClient.getForProduct(eq("42"), anyInt()))
                .thenReturn(Mono.just(List.of(
                        new RecommendationDto("100", 9),
                        new RecommendationDto("101", 7)
                )));
        when(productClient.findByIds(any())).thenAnswer(inv -> {
            List<String> ids = inv.getArgument(0);
            return Mono.just(java.util.Map.of(
                    "100", new ProductDto("100", "A", null, new BigDecimal("1"), "USD", List.of(), null, 1, true),
                    "101", new ProductDto("101", "B", null, new BigDecimal("2"), "USD", List.of(), null, 0, false)
            ));
        });

        tester.document("query { recommendations(productId:\"42\", limit:2) { id name } }")
                .execute()
                .path("recommendations").entityList(Object.class).hasSize(2);
    }

    @Test
    void should_defaultPriceToZero_when_priceMissing() {
        ProductDto p = new ProductDto("1", "P", null, null, null,
                null, null, null, null);
        when(productClient.findById("1")).thenReturn(Mono.just(p));

        tester.document("query { product(id:\"1\") { price currency inStock images } }")
                .execute()
                .path("product.price").entity(Double.class).isEqualTo(0.0)
                .path("product.currency").entity(String.class).isEqualTo("USD")
                .path("product.inStock").entity(Boolean.class).isEqualTo(false)
                .path("product.images").entityList(String.class).hasSize(0);
    }
}
