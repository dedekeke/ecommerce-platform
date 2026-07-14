package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.client.ProductServiceGateway;
import com.ecommerce.cartservice.client.UserServiceClient;
import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartStatus;
import com.ecommerce.cartservice.dto.AddToCartRequest;
import com.ecommerce.cartservice.dto.ProductDto;
import com.ecommerce.cartservice.dto.UserContactDto;
import com.ecommerce.cartservice.repository.CartItemRepository;
import com.ecommerce.cartservice.repository.CartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the email denormalisation added to {@link CartService} so that
 * the abandoned-cart scanner has a recipient (§3.10 follow-up).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService email resolution")
class CartServiceEmailResolutionTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductServiceGateway productServiceGateway;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private CartService cartService;

    private static final String USER_ID = "auth0|user123";
    private static final String PRODUCT_ID = "product-001";

    private AddToCartRequest addRequest() {
        AddToCartRequest request = new AddToCartRequest();
        request.setProductId(PRODUCT_ID);
        request.setQuantity(1);
        return request;
    }

    private ProductDto validProduct() {
        ProductDto product = new ProductDto();
        product.setId(PRODUCT_ID);
        product.setName("Test Product");
        product.setSku("SKU-001");
        product.setPrice(new BigDecimal("29.99"));
        product.setStockQuantity(100);
        product.setActive(true);
        return product;
    }

    private Cart activeCart(String email) {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(USER_ID);
        cart.setStatus(CartStatus.ACTIVE);
        cart.setUserEmail(email);
        return cart;
    }

    @Test
    @DisplayName("should_resolveAndStoreEmail_when_cartHasNone")
    void should_resolveAndStoreEmail_when_cartHasNone() {
        Cart cart = activeCart(null);
        when(productServiceGateway.getProductById(PRODUCT_ID)).thenReturn(validProduct());
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID)).thenReturn(Optional.empty());
        when(userServiceClient.getUserByAuth0Id(USER_ID))
                .thenReturn(UserContactDto.builder().id("1").email("shopper@example.com").fullName("Shopper").build());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.addItemToCart(USER_ID, addRequest());

        assertThat(cart.getUserEmail()).isEqualTo("shopper@example.com");
    }

    @Test
    @DisplayName("should_leaveEmailNull_when_userServiceFails")
    void should_leaveEmailNull_when_userServiceFails() {
        Cart cart = activeCart(null);
        when(productServiceGateway.getProductById(PRODUCT_ID)).thenReturn(validProduct());
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID)).thenReturn(Optional.empty());
        when(userServiceClient.getUserByAuth0Id(USER_ID)).thenThrow(new RuntimeException("user-service down"));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.addItemToCart(USER_ID, addRequest());

        assertThat(cart.getUserEmail()).isNull();
    }

    @Test
    @DisplayName("should_notReResolve_when_emailAlreadyStored")
    void should_notReResolve_when_emailAlreadyStored() {
        Cart cart = activeCart("existing@example.com");
        when(productServiceGateway.getProductById(PRODUCT_ID)).thenReturn(validProduct());
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        cartService.addItemToCart(USER_ID, addRequest());

        assertThat(cart.getUserEmail()).isEqualTo("existing@example.com");
        verify(userServiceClient, never()).getUserByAuth0Id(any());
    }
}
