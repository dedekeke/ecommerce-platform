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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract test for the authenticated {@code POST /api/cart/merge} claim endpoint.
 * A custom argument resolver supplies the {@code @AuthenticationPrincipal Jwt} so
 * the standalone MockMvc setup needs no security context.
 *
 * <p>The endpoint takes NO request body: the guest email is resolved server-side
 * from the caller's verified account email, so the client cannot assert whose
 * cart to claim.</p>
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
    @DisplayName("Should merge the caller's guest cart keyed by the authenticated subject")
    void should_mergeGuestCart_forAuthenticatedCaller() throws Exception {
        CartResponse merged = CartResponse.builder()
                .id("1").userId(USER_ID).items(List.of()).status("ACTIVE").build();
        when(cartService.mergeGuestCartIntoUser(USER_ID)).thenReturn(merged);

        mockMvc.perform(post("/api/cart/merge"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(USER_ID));

        verify(cartService).mergeGuestCartIntoUser(USER_ID);
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
