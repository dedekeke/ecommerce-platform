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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contract tests for the anonymous {@link GuestCartController}. Standalone
 * MockMvc (no Spring context / DB / Docker): the derived owner id is asserted to
 * flow from the {@code X-Guest-Email} header into the service, and header/validation
 * errors map through {@link GlobalExceptionHandler} to 400.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuestCartController Contract Tests")
class GuestCartControllerTest {

    @Mock
    private CartService cartService;

    @InjectMocks
    private GuestCartController controller;

    private MockMvc mockMvc;

    private static final String EMAIL = "guest@example.com";
    private static final String OWNER = "guest:deadbeef";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(null))
                .build();
    }

    private static CartResponse cart() {
        return CartResponse.builder()
                .id("1").userId(OWNER).items(List.of()).status("ACTIVE").build();
    }

    @Test
    @DisplayName("Should derive owner from header and return the guest cart")
    void should_returnGuestCart_when_getWithHeader() throws Exception {
        when(cartService.guestCartOwnerId(EMAIL)).thenReturn(OWNER);
        when(cartService.getOrCreateCart(OWNER)).thenReturn(cart());

        mockMvc.perform(get("/api/cart/guest").header("X-Guest-Email", EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(OWNER));

        verify(cartService).getOrCreateCart(OWNER);
    }

    @Test
    @DisplayName("Should return 400 when the guest email header is missing")
    void should_return400_when_headerMissing() throws Exception {
        mockMvc.perform(get("/api/cart/guest"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 400 when the guest email is blank")
    void should_return400_when_emailBlank() throws Exception {
        when(cartService.guestCartOwnerId(" "))
                .thenThrow(new IllegalArgumentException("Guest email must not be blank"));

        mockMvc.perform(get("/api/cart/guest").header("X-Guest-Email", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should add an item to the guest cart keyed by the derived owner")
    void should_addItem_when_postWithHeaderAndBody() throws Exception {
        when(cartService.guestCartOwnerId(EMAIL)).thenReturn(OWNER);
        when(cartService.addItemToCart(eq(OWNER), any())).thenReturn(cart());

        mockMvc.perform(post("/api/cart/guest/items")
                        .header("X-Guest-Email", EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\",\"quantity\":2}"))
                .andExpect(status().isOk());

        verify(cartService).addItemToCart(eq(OWNER), any());
    }

    @Test
    @DisplayName("Should reject an add with quantity below 1")
    void should_return400_when_quantityInvalid() throws Exception {
        mockMvc.perform(post("/api/cart/guest/items")
                        .header("X-Guest-Email", EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"p1\",\"quantity\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should update a guest cart item")
    void should_updateItem_when_putWithHeader() throws Exception {
        when(cartService.guestCartOwnerId(EMAIL)).thenReturn(OWNER);
        when(cartService.updateCartItem(eq(OWNER), eq(5L), any())).thenReturn(cart());

        mockMvc.perform(put("/api/cart/guest/items/5")
                        .header("X-Guest-Email", EMAIL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk());

        verify(cartService).updateCartItem(eq(OWNER), eq(5L), any());
    }

    @Test
    @DisplayName("Should remove a guest cart item")
    void should_removeItem_when_deleteWithHeader() throws Exception {
        when(cartService.guestCartOwnerId(EMAIL)).thenReturn(OWNER);
        when(cartService.removeItemFromCart(OWNER, 5L)).thenReturn(cart());

        mockMvc.perform(delete("/api/cart/guest/items/5").header("X-Guest-Email", EMAIL))
                .andExpect(status().isOk());

        verify(cartService).removeItemFromCart(OWNER, 5L);
    }

    @Test
    @DisplayName("Should clear the guest cart returning 204")
    void should_clearCart_when_deleteClear() throws Exception {
        when(cartService.guestCartOwnerId(EMAIL)).thenReturn(OWNER);

        mockMvc.perform(delete("/api/cart/guest/clear").header("X-Guest-Email", EMAIL))
                .andExpect(status().isNoContent());

        verify(cartService).clearCart(OWNER);
    }
}
