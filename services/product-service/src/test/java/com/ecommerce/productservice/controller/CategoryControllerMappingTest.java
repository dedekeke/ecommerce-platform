package com.ecommerce.productservice.controller;

import com.ecommerce.productservice.mapper.CategoryMapper;
import com.ecommerce.productservice.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Request-mapping contract test for {@link CategoryController}.
 *
 * <p>The API gateway forwards BOTH the unversioned ({@code /api/categories/**}) and
 * the versioned ({@code /api/v1/categories/**}) routes to product-service as the
 * post-rewrite path {@code /api/categories/**} (the v1 route strips the {@code /v1}
 * prefix via RewritePath; the unversioned route forwards as-is). The controller must
 * therefore answer on {@code /api/categories} — the path the service actually
 * receives — mirroring the {@link ProductController} convention ({@code /api/products}).
 *
 * <p>Regression guard for the routing bug where the controller was mapped at the
 * literal {@code /api/v1/categories}, which no gateway-mediated request ever carries,
 * so category browsing was broken through the gateway.
 *
 * <p>Standalone MockMvc setup exercises the real Spring MVC handler mapping without
 * booting the application context.
 */
@ExtendWith(MockitoExtension.class)
class CategoryControllerMappingTest {

    @Mock
    private CategoryService categoryService;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryController categoryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(categoryController).build();
        lenient().when(categoryService.getAllCategories()).thenReturn(Collections.emptyList());
        lenient().when(categoryService.getRootCategories()).thenReturn(Collections.emptyList());
    }

    @Test
    void should_answerOnPostRewritePath_when_getCategories() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk());
    }

    @Test
    void should_answerOnPostRewritePath_when_getRootCategories() throws Exception {
        mockMvc.perform(get("/api/categories/root"))
                .andExpect(status().isOk());
    }

    @Test
    void should_notAnswerOnLiteralV1Path_when_getCategories() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isNotFound());
    }
}
