package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.service.CategoryService;
import com.ecommerce.productservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Serialization contract tests for the paginated {@link ProductController} endpoints.
 *
 * <p>Regression coverage for the bug where these endpoints returned a raw Spring
 * Data {@code PageImpl}. Spring Data explicitly declares this JSON non-portable:
 * it leaks the internal {@code pageable}/{@code sort} structure, has no stable
 * contract, and hard-fails serialization (HTTP 500) on the Spring Boot 3.3+
 * upgrade path. The endpoints must emit a stable paged envelope exposing only
 * the fields clients depend on.
 *
 * <p>Uses a standalone MockMvc setup (real Spring MVC return-value handling and
 * Jackson serialization) so the test verifies the JSON contract without booting
 * the full application context, which component-scans {@code com.ecommerce.common}
 * JPA/Kafka configuration unrelated to this controller.
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerPageSerializationTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private ProductController productController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
    }

    private static ProductResponse sampleResponse() {
        return ProductResponse.builder()
                .id("1")
                .sku("SKU-1")
                .name("Sample")
                .build();
    }

    @Test
    void should_omitPageableAndSort_when_getAllProducts() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1);
        lenient().when(productService.getAllProducts(any())).thenReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
    }

    @Test
    void should_exposeStablePageFields_when_getAllProducts() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1);
        lenient().when(productService.getAllProducts(any())).thenReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true))
                .andExpect(jsonPath("$.numberOfElements").value(1))
                .andExpect(jsonPath("$.empty").value(false));
    }

    @Test
    void should_omitPageableAndSort_when_getFeaturedProducts() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 10), 1);
        lenient().when(productService.getFeaturedProducts(any())).thenReturn(page);

        mockMvc.perform(get("/api/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
    }

    @Test
    void should_exposeStablePageFields_when_getFeaturedProducts() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 10), 1);
        lenient().when(productService.getFeaturedProducts(any())).thenReturn(page);

        mockMvc.perform(get("/api/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
