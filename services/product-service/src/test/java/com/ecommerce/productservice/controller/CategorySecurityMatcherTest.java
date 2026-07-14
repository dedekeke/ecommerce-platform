package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.config.ProductSecurityConfig;
import com.ecommerce.productservice.mapper.CategoryMapper;
import com.ecommerce.productservice.model.Category;
import com.ecommerce.productservice.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization drift guard for the category endpoints in product-service's OWN
 * filter chain ({@link ProductSecurityConfig}).
 *
 * <p>The gateway rewrites both {@code /api/categories/**} and {@code /api/v1/categories/**}
 * to the unversioned {@code /api/categories/**} before forwarding, and internal
 * service-to-service callers reach product-service via load-balanced clients that
 * BYPASS the gateway. The service layer is therefore the real defense and its matchers
 * must key on the post-rewrite {@code /api/categories/**} shape — the same path the
 * {@link CategoryController} now maps. Reads are public; POST/PUT/PATCH/DELETE require
 * {@code SCOPE_admin}. If a matcher regressed to {@code /api/v1/categories} it would
 * stop matching real traffic and these tests would fail.
 *
 * <p>Only {@link CategoryController} is wired so requests map to a handler; the config's
 * {@link JwtDecoder} bean is mocked (config methods are CGLIB-proxied so the real
 * Auth0 discovery call is never made) and {@code jwt()} supplies the principal.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = CategorySecurityMatcherTest.TestContext.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "security.enabled=true",
        "auth0.domain=test-tenant.auth0.com",
        "auth0.audience=https://api.test/"
})
class CategorySecurityMatcherTest {

    @Configuration
    @EnableWebMvc
    @Import(ProductSecurityConfig.class)
    static class TestContext {
        @Bean
        CategoryController categoryController(CategoryService categoryService,
                                              CategoryMapper categoryMapper) {
            return new CategoryController(categoryService, categoryMapper);
        }
    }

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
    void should_allowPublicRead_when_getCategoriesAnonymously() throws Exception {
        when(categoryService.getAllCategories()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
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
    void should_return401_when_unauthenticatedMovesCategory() throws Exception {
        mockMvc.perform(patch("/api/categories/1/move")
                        .param("newParentId", "2"))
                .andExpect(status().isUnauthorized());

        verify(categoryService, never()).moveCategory(anyLong(), anyLong());
    }

    @Test
    void should_return403_when_nonAdminDeletesCategory() throws Exception {
        mockMvc.perform(delete("/api/categories/1").with(nonAdmin()))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).deleteCategory(anyLong());
    }

    @Test
    void should_return403_when_nonAdminCreatesCategory() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType("application/json")
                        .content("{\"name\":\"x\",\"slug\":\"x\"}")
                        .with(nonAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void should_return403_when_nonAdminUpdatesCategory() throws Exception {
        mockMvc.perform(put("/api/categories/1")
                        .contentType("application/json")
                        .content("{\"name\":\"x\",\"slug\":\"x\"}")
                        .with(nonAdmin()))
                .andExpect(status().isForbidden());
    }
}
