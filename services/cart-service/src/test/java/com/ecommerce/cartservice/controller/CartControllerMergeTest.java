package com.ecommerce.cartservice.controller;

import com.ecommerce.cartservice.dto.CartResponse;
import com.ecommerce.cartservice.exception.GlobalExceptionHandler;
import com.ecommerce.cartservice.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for the authenticated {@code POST /api/cart/merge} claim endpoint.
 * A custom argument resolver supplies the {@code @AuthenticationPrincipal Jwt} so
 * the standalone MockMvc setup needs no security context.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartController Merge Endpoint Tests")
class CartControllerMergeTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private CartController controller;

    private MockMvc mockMvc;

    private static final String USER_ID = "auth0|user123";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .setCustomArgumentResolvers(new JwtResolver(USER_ID))
                .build();
    }

    @Test
    @DisplayName("Should merge the guest cart into the authenticated user's cart")
    void should_mergeGuestCart_when_validEmail() throws Exception {
        CartResponse merged = CartResponse.builder()
                .id("1").userId(USER_ID).items(List.of()).status("ACTIVE").build();
        when(cartService.mergeGuestCartIntoUser(USER_ID, "guest@example.com"))
                .thenReturn(merged);

        mockMvc.perform(post("/api/cart/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestEmail\":\"guest@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID));

        verify(cartService).mergeGuestCartIntoUser(USER_ID, "guest@example.com");
    }

    @Test
    @DisplayName("Should return 400 when the guest email is not a valid address")
    void should_return400_when_emailInvalid() throws Exception {
        mockMvc.perform(post("/api/cart/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestEmail\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when the guest email is blank")
    void should_return400_when_emailBlank() throws Exception {
        mockMvc.perform(post("/api/cart/merge")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"guestEmail\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    /** Supplies a fixed {@link Jwt} for the {@code @AuthenticationPrincipal Jwt} parameter. */
    private record JwtResolver(String subject) implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return Jwt.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        }
    }
}
