package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.client.ProductServiceClient;
import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartItem;
import com.ecommerce.cartservice.domain.CartStatus;
import com.ecommerce.cartservice.dto.AddToCartRequest;
import com.ecommerce.cartservice.dto.CartResponse;
import com.ecommerce.cartservice.dto.ProductDto;
import com.ecommerce.cartservice.dto.UpdateCartItemRequest;
import com.ecommerce.cartservice.exception.CartItemNotFoundException;
import com.ecommerce.cartservice.exception.CartNotFoundException;
import com.ecommerce.cartservice.exception.ProductNotAvailableException;
import com.ecommerce.cartservice.repository.CartItemRepository;
import com.ecommerce.cartservice.repository.CartRepository;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cart Service Unit Tests")
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private com.ecommerce.cartservice.client.UserServiceClient userServiceClient;

    @InjectMocks
    private CartService cartService;

    private static final String USER_ID = "auth0|user123";
    private static final String PRODUCT_ID = "product-001";

    private ProductDto createValidProduct() {
        ProductDto product = new ProductDto();
        product.setId(PRODUCT_ID);
        product.setName("Test Product");
        product.setSku("SKU-001");
        product.setPrice(new BigDecimal("29.99"));
        product.setStockQuantity(100);
        product.setActive(true);
        product.setImageUrl("http://example.com/image.jpg");
        return product;
    }

    private Cart createActiveCart() {
        Cart cart = new Cart();
        cart.setId(1L);
        cart.setUserId(USER_ID);
        cart.setStatus(CartStatus.ACTIVE);
        return cart;
    }

    @Nested
    @DisplayName("Add Item to Cart Tests")
    class AddItemToCartTests {

        @Test
        @DisplayName("Should add item to cart when product is available and in stock")
        void shouldAddItemWhenProductAvailable() {
            // Given
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(PRODUCT_ID);
            request.setQuantity(2);

            ProductDto product = createValidProduct();
            Cart cart = createActiveCart();

            when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(product);
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID))
                    .thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            // When
            CartResponse result = cartService.addItemToCart(USER_ID, request);

            // Then
            assertThat(result).isNotNull();
            verify(productServiceClient).getProductById(PRODUCT_ID);
            verify(cartRepository).save(any(Cart.class));
        }

        @Test
        @DisplayName("Should throw exception when product service is unavailable")
        void shouldThrowExceptionWhenProductServiceUnavailable() {
            // Given
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(PRODUCT_ID);
            request.setQuantity(1);

            when(productServiceClient.getProductById(PRODUCT_ID))
                    .thenThrow(mock(FeignException.class));

            // When/Then
            assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, request))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining("Product not available");
        }

        @Test
        @DisplayName("Should throw exception when product is not found")
        void shouldThrowExceptionWhenProductNotFound() {
            // Given
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(PRODUCT_ID);
            request.setQuantity(1);

            when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(null);

            // When/Then
            assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, request))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining("Product not found");
        }

        @Test
        @DisplayName("Should throw exception when product is out of stock")
        void shouldThrowExceptionWhenProductOutOfStock() {
            // Given
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(PRODUCT_ID);
            request.setQuantity(10);

            ProductDto product = createValidProduct();
            product.setStockQuantity(5); // Less than requested

            when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(product);

            // When/Then
            assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, request))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining("Insufficient stock");
        }

        @Test
        @DisplayName("Should throw exception when product is not active")
        void shouldThrowExceptionWhenProductNotActive() {
            // Given
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(PRODUCT_ID);
            request.setQuantity(1);

            ProductDto product = createValidProduct();
            product.setActive(false);

            when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(product);

            // When/Then
            assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, request))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining("not available");
        }

        @Test
        @DisplayName("Should throw exception when product has null stock quantity")
        void shouldThrowExceptionWhenProductHasNullStock() {
            // Given
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(PRODUCT_ID);
            request.setQuantity(1);

            ProductDto product = createValidProduct();
            product.setStockQuantity(null);

            when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(product);

            // When/Then
            assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, request))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining("Stock information unavailable");
        }

        @Test
        @DisplayName("Should throw exception when product has null price")
        void shouldThrowExceptionWhenProductHasNullPrice() {
            // Given
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(PRODUCT_ID);
            request.setQuantity(1);

            ProductDto product = createValidProduct();
            product.setPrice(null);

            when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(product);

            // When/Then
            assertThatThrownBy(() -> cartService.addItemToCart(USER_ID, request))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining("Price information unavailable");
        }

        @Test
        @DisplayName("Should update quantity when product already exists in cart")
        void shouldUpdateQuantityWhenProductExistsInCart() {
            // Given
            AddToCartRequest request = new AddToCartRequest();
            request.setProductId(PRODUCT_ID);
            request.setQuantity(2);

            ProductDto product = createValidProduct();
            Cart cart = createActiveCart();

            CartItem existingItem = new CartItem();
            existingItem.setId(1L);
            existingItem.setCart(cart);
            existingItem.setProductId(PRODUCT_ID);
            existingItem.setQuantity(3);
            existingItem.setPriceSnapshot(product.getPrice());

            when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(product);
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(cart));
            when(cartItemRepository.findByCartIdAndProductId(cart.getId(), PRODUCT_ID))
                    .thenReturn(Optional.of(existingItem));
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            // When
            CartResponse result = cartService.addItemToCart(USER_ID, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(existingItem.getQuantity()).isEqualTo(5); // 3 + 2
            verify(cartRepository).save(any(Cart.class));
        }
    }

    @Nested
    @DisplayName("Update Cart Item Tests")
    class UpdateCartItemTests {

        @Test
        @DisplayName("Should update cart item quantity successfully")
        void shouldUpdateCartItemQuantity() {
            // Given
            Long itemId = 1L;
            UpdateCartItemRequest request = new UpdateCartItemRequest();
            request.setQuantity(5);

            Cart cart = createActiveCart();
            ProductDto product = createValidProduct();

            CartItem item = new CartItem();
            item.setId(itemId);
            item.setCart(cart);
            item.setProductId(PRODUCT_ID);
            item.setQuantity(2);
            item.setPriceSnapshot(product.getPrice());
            cart.getItems().add(item);

            when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(product);
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            // When
            CartResponse result = cartService.updateCartItem(USER_ID, itemId, request);

            // Then
            assertThat(result).isNotNull();
            assertThat(item.getQuantity()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should throw exception when cart item not found")
        void shouldThrowExceptionWhenCartItemNotFound() {
            // Given
            Long itemId = 999L;
            UpdateCartItemRequest request = new UpdateCartItemRequest();
            request.setQuantity(5);

            when(cartItemRepository.findById(itemId)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, itemId, request))
                    .isInstanceOf(CartItemNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw exception when updating item exceeds available stock")
        void shouldThrowExceptionWhenUpdateExceedsStock() {
            // Given
            Long itemId = 1L;
            UpdateCartItemRequest request = new UpdateCartItemRequest();
            request.setQuantity(200); // More than stock

            Cart cart = createActiveCart();
            ProductDto product = createValidProduct();
            product.setStockQuantity(100);

            CartItem item = new CartItem();
            item.setId(itemId);
            item.setCart(cart);
            item.setProductId(PRODUCT_ID);
            item.setQuantity(2);
            item.setPriceSnapshot(product.getPrice());
            cart.getItems().add(item);

            when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(item));
            when(productServiceClient.getProductById(PRODUCT_ID)).thenReturn(product);

            // When/Then
            assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, itemId, request))
                    .isInstanceOf(ProductNotAvailableException.class)
                    .hasMessageContaining("Insufficient stock");
        }

        @Test
        @DisplayName("Should throw exception when cart belongs to different user")
        void shouldThrowExceptionWhenCartBelongsToDifferentUser() {
            // Given
            Long itemId = 1L;
            UpdateCartItemRequest request = new UpdateCartItemRequest();
            request.setQuantity(5);

            Cart otherUserCart = createActiveCart();
            otherUserCart.setUserId("other-user-id"); // Different user

            CartItem item = new CartItem();
            item.setId(itemId);
            item.setCart(otherUserCart);
            item.setProductId(PRODUCT_ID);

            when(cartItemRepository.findById(itemId)).thenReturn(Optional.of(item));

            // When/Then
            assertThatThrownBy(() -> cartService.updateCartItem(USER_ID, itemId, request))
                    .isInstanceOf(CartNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("Get Cart Tests")
    class GetCartTests {

        @Test
        @DisplayName("Should create new cart when none exists")
        void shouldCreateNewCartWhenNoneExists() {
            // Given
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
                Cart cart = invocation.getArgument(0);
                cart.setId(1L);
                return cart;
            });

            // When
            CartResponse result = cartService.getOrCreateCart(USER_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getStatus()).isEqualTo(CartStatus.ACTIVE.name());
            verify(cartRepository).save(any(Cart.class));
        }

        @Test
        @DisplayName("Should return existing cart when one exists")
        void shouldReturnExistingCart() {
            // Given
            Cart existingCart = createActiveCart();

            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(existingCart));

            // When
            CartResponse result = cartService.getOrCreateCart(USER_ID);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(existingCart.getId().toString());
            verify(cartRepository, never()).save(any(Cart.class));
        }
    }
}
