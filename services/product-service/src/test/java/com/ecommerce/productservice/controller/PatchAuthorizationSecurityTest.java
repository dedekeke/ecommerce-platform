package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.config.ProductSecurityConfig;
import com.ecommerce.productservice.mapper.CategoryMapper;
import com.ecommerce.productservice.mapper.ProductMapper;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.model.Product;
import com.ecommerce.productservice.service.CategoryService;
import com.ecommerce.productservice.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization tests for the catalog PATCH endpoints, closing — in the service's
 * OWN filter chain — the gap PR#108 fixed at the gateway. Internal service-to-service
 * callers reach product-service via load-balanced clients that BYPASS the gateway, so
 * the service layer is the real defense. No internal caller PATCHes product stock
 * (order placement reserves stock in inventory-service over gRPC; cart/order product
 * clients are GET-only), therefore both PATCH endpoints require {@code SCOPE_admin}.
 *
 * The test wires a minimal web context containing ONLY the real
 * {@link ProductSecurityConfig} filter chain and the two controllers — no persistence
 * or discovery layer — so it exercises the authorization rules directly and fast. The
 * {@code jwt()} post-processor supplies the principal; {@link JwtDecoder} is mocked so
 * no Auth0 call is made (the config's decoder bean is never instantiated).
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = PatchAuthorizationSecurityTest.TestContext.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "auth0.domain=test-tenant.auth0.com",
        "auth0.audience=https://api.test/"
})
class PatchAuthorizationSecurityTest {

    @Configuration
    @EnableWebMvc
    @Import(ProductSecurityConfig.class)
    static class TestContext {

        @org.springframework.context.annotation.Bean
        ProductController productController(ProductService productService,
                                            ProductMapper productMapper,
                                            CategoryService categoryService) {
            return new ProductController(productService, productMapper, categoryService);
        }

        @org.springframework.context.annotation.Bean
        CategoryController categoryController(CategoryService categoryService,
                                              CategoryMapper categoryMapper) {
            return new CategoryController(categoryService, categoryMapper);
        }
    }

    @MockBean
    private ProductService productService;

    @MockBean
    private ProductMapper productMapper;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private CategoryMapper categoryMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private static RequestPostProcessor nonAdmin() {
        return jwt().jwt(jwt -> jwt.claim("scope", "read:products"));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_admin"));
    }

    @Test
    void should_return403_when_nonAdminPatchesStock() throws Exception {
        mockMvc.perform(patch("/api/products/1/stock")
                        .param("quantity", "5")
                        .with(nonAdmin()))
                .andExpect(status().isForbidden());

        verify(productService, never()).updateStockQuantity(anyLong(), anyInt());
    }

    @Test
    void should_allowStockPatch_when_adminScope() throws Exception {
        when(productService.updateStockQuantity(1L, 5)).thenReturn(new Product());

        mockMvc.perform(patch("/api/products/1/stock")
                        .param("quantity", "5")
                        .with(admin()))
                .andExpect(status().isOk());

        verify(productService).updateStockQuantity(1L, 5);
    }

    @Test
    void should_return401_when_unauthenticatedPatchesStock() throws Exception {
        mockMvc.perform(patch("/api/products/1/stock")
                        .param("quantity", "5"))
                .andExpect(status().isUnauthorized());

        verify(productService, never()).updateStockQuantity(anyLong(), anyInt());
    }

    @Test
    void should_return403_when_nonAdminMovesCategory() throws Exception {
        mockMvc.perform(patch("/api/categories/1/move")
                        .param("newParentId", "2")
                        .with(nonAdmin()))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).moveCategory(anyLong(), anyLong());
    }

    @Test
    void should_allowCategoryMove_when_adminScope() throws Exception {
        when(categoryService.moveCategory(1L, 2L)).thenReturn(new Category());

        mockMvc.perform(patch("/api/categories/1/move")
                        .param("newParentId", "2")
                        .with(admin()))
                .andExpect(status().isOk());

        verify(categoryService).moveCategory(1L, 2L);
    }

    @Test
    void should_allowPublicRead_when_getCategoriesAnonymously() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }
}
