package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.dto.ProductResponse;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.service.CategoryService;
import com.ecommerce.productservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API contract tests for stock ownership: {@code stockQuantity} is create-only on
 * the product request body, and only {@code PATCH /api/products/{id}/stock} moves
 * the catalog stock snapshot afterwards. See {@code ProductStockOwnershipTest} for
 * the rationale (inventory-service owns stock movement).
 */
@ExtendWith(MockitoExtension.class)
class ProductStockContractTest {

    @Mock
    private ProductService productService;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private ProductController productController;

    private MockMvc mockMvc;

    private static final String PUT_BODY_WITH_STOCK = """
            {
              "sku": "SKU-42",
              "name": "New name",
              "price": 19.99,
              "currency": "USD",
              "stockQuantity": 999
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(productController).build();
    }

    @Test
    void should_notTouchStock_when_putCarriesStockQuantity() throws Exception {
        when(productMapper.toEntity(any())).thenReturn(new Product());
        when(productService.updateProduct(eq(42L), any(Product.class))).thenReturn(new Product());
        when(productMapper.toResponse(any(Product.class)))
                .thenReturn(ProductResponse.builder().id("42").stockQuantity(50).build());

        mockMvc.perform(put("/api/products/42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PUT_BODY_WITH_STOCK))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(50));

        verify(productService, never()).updateStockQuantity(anyLong(), anyInt());
    }

    @Test
    void should_moveStock_when_dedicatedStockEndpointCalled() throws Exception {
        when(productService.updateStockQuantity(42L, 7)).thenReturn(new Product());
        when(productMapper.toResponse(any(Product.class)))
                .thenReturn(ProductResponse.builder().id("42").stockQuantity(7).build());

        mockMvc.perform(patch("/api/products/42/stock").param("quantity", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(7));

        verify(productService).updateStockQuantity(42L, 7);
    }
}
