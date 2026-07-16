package com.ecommerce.cartservice.service;

import com.ecommerce.cartservice.client.ProductServiceGateway;
import com.ecommerce.cartservice.client.UserServiceClient;
import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartItem;
import com.ecommerce.cartservice.domain.CartStatus;
import com.ecommerce.cartservice.dto.CartResponse;
import com.ecommerce.cartservice.dto.UserContactDto;
import com.ecommerce.cartservice.repository.CartItemRepository;
import com.ecommerce.cartservice.repository.CartRepository;
import com.ecommerce.cartservice.security.GuestIdentityFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the merge-on-login / guest-identity seam. Uses a REAL
 * {@link GuestIdentityFactory} so identity derivation (and the cross-service
 * contract) is exercised end-to-end, with the persistence layer mocked.
 *
 * <p>Security-critical: the guest email is resolved server-side from the caller's
 * VERIFIED email (user-service), never from client input — so a caller can only
 * claim their own guest cart. The ownership-mismatch test pins that guarantee.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CartService Guest Merge Unit Tests")
class CartServiceMergeTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductServiceGateway productServiceGateway;
    @Mock
    private UserServiceClient userServiceClient;

    private final GuestIdentityFactory guestIdentityFactory = new GuestIdentityFactory();

    private CartService cartService;

    private static final String USER_ID = "auth0|user123";
    private static final String VERIFIED_EMAIL = "guest@example.com";
    private String guestId;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository,
                productServiceGateway, userServiceClient, guestIdentityFactory);
        guestId = guestIdentityFactory.guestId(VERIFIED_EMAIL);
    }

    private void callerVerifiedEmailIs(String email) {
        UserContactDto contact = UserContactDto.builder().id(USER_ID).email(email).build();
        when(userServiceClient.getUserByAuth0Id(USER_ID)).thenReturn(contact);
    }

    private CartItem item(String productId, int qty, String price) {
        CartItem ci = CartItem.builder()
                .id(1L)
                .productId(productId)
                .productName("Product " + productId)
                .priceSnapshot(new BigDecimal(price))
                .quantity(qty)
                .build();
        ci.calculateSubtotal();
        return ci;
    }

    private Cart cartFor(String ownerId, long id, CartItem... items) {
        Cart cart = new Cart();
        cart.setId(id);
        cart.setUserId(ownerId);
        cart.setStatus(CartStatus.ACTIVE);
        for (CartItem i : items) {
            cart.addItem(i);
        }
        return cart;
    }

    @Test
    @DisplayName("Should derive the same identity order-service uses for the guest owner")
    void should_deriveGuestOwnerId_matchingOrderService() {
        assertThat(cartService.guestCartOwnerId(VERIFIED_EMAIL))
                .isEqualTo("guest:513935c4d2db2d2d984dff1d68397f6e2ac8c4e5c48c92bd98e02bdc90b7aefe");
    }

    @Test
    @DisplayName("Should move guest items into a fresh user cart when the user has none")
    void should_moveGuestItems_when_userHasNoCart() {
        callerVerifiedEmailIs(VERIFIED_EMAIL);
        Cart guestCart = cartFor(guestId, 10L, item("p1", 2, "10.00"));
        when(cartRepository.findByUserIdAndStatus(guestId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartItemRepository.findByCartIdAndProductId(any(), eq("p1")))
                .thenReturn(Optional.empty());

        CartResponse result = cartService.mergeGuestCartIntoUser(USER_ID);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(2);
        verify(cartRepository).delete(guestCart);
        // Merge must not depend on product-service availability.
        verify(productServiceGateway, never()).getProductById(any());
    }

    @Test
    @DisplayName("Should sum quantities when the user already has the same product")
    void should_sumQuantities_when_productOverlaps() {
        callerVerifiedEmailIs(VERIFIED_EMAIL);
        Cart guestCart = cartFor(guestId, 10L, item("p1", 3, "10.00"));

        CartItem userItem = item("p1", 1, "10.00");
        userItem.setId(99L);
        Cart userCart = cartFor(USER_ID, 20L, userItem);

        when(cartRepository.findByUserIdAndStatus(guestId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(guestCart));
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                .thenReturn(Optional.of(userCart));
        when(cartItemRepository.findByCartIdAndProductId(20L, "p1"))
                .thenReturn(Optional.of(userItem));
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

        CartResponse result = cartService.mergeGuestCartIntoUser(USER_ID);

        assertThat(userItem.getQuantity()).isEqualTo(4); // 1 + 3
        assertThat(result.getItems()).hasSize(1);
        verify(cartRepository).delete(guestCart);
    }

    @Test
    @DisplayName("Should NEVER claim another user's guest cart (ownership derived from verified email)")
    void should_notClaimOtherUsersGuestCart_when_callerEmailDiffers() {
        // The caller's verified email is the attacker's own address. A victim's
        // guest cart exists under the victim's email-derived identity. Because the
        // merge derives the guest id ONLY from the caller's verified email, the
        // victim cart is never looked up, merged, or deleted.
        callerVerifiedEmailIs("attacker@example.com");
        String attackerGuestId = guestIdentityFactory.guestId("attacker@example.com");
        String victimGuestId = guestIdentityFactory.guestId("victim@example.com");
        Cart victimCart = cartFor(victimGuestId, 77L, item("p1", 5, "10.00"));

        // Attacker has no guest cart of their own.
        when(cartRepository.findByUserIdAndStatus(attackerGuestId, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            c.setId(30L);
            return c;
        });
        // Stub the victim lookup defensively; it must never be invoked.
        lenient().when(cartRepository.findByUserIdAndStatus(victimGuestId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(victimCart));

        CartResponse result = cartService.mergeGuestCartIntoUser(USER_ID);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        assertThat(result.getItems()).isEmpty();
        verify(cartRepository, never()).findByUserIdAndStatus(victimGuestId, CartStatus.ACTIVE);
        verify(cartRepository, never()).delete(victimCart);
    }

    @Test
    @DisplayName("Should be a no-op when the caller has no verified email to derive identity from")
    void should_noOp_when_callerEmailUnresolvable() {
        when(userServiceClient.getUserByAuth0Id(USER_ID)).thenReturn(null);
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            c.setId(30L);
            return c;
        });

        CartResponse result = cartService.mergeGuestCartIntoUser(USER_ID);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        verify(cartRepository, never()).delete(any(Cart.class));
    }

    @Test
    @DisplayName("Should be a no-op returning the user cart when no guest cart exists")
    void should_returnUserCart_when_noGuestCart() {
        callerVerifiedEmailIs(VERIFIED_EMAIL);
        when(cartRepository.findByUserIdAndStatus(guestId, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            c.setId(30L);
            return c;
        });

        CartResponse result = cartService.mergeGuestCartIntoUser(USER_ID);

        assertThat(result.getUserId()).isEqualTo(USER_ID);
        verify(cartRepository, never()).delete(any(Cart.class));
    }

    @Test
    @DisplayName("Should delete an empty guest cart without touching the user cart items")
    void should_deleteEmptyGuestCart_when_guestCartHasNoItems() {
        callerVerifiedEmailIs(VERIFIED_EMAIL);
        Cart emptyGuestCart = cartFor(guestId, 10L); // no items
        when(cartRepository.findByUserIdAndStatus(guestId, CartStatus.ACTIVE))
                .thenReturn(Optional.of(emptyGuestCart));
        when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            c.setId(40L);
            return c;
        });

        CartResponse result = cartService.mergeGuestCartIntoUser(USER_ID);

        assertThat(result.getItems()).isEmpty();
        verify(cartRepository).delete(emptyGuestCart);
    }
}
