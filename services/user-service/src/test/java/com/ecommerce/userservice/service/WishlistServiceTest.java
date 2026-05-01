package com.ecommerce.userservice.service;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserRole;
import com.ecommerce.userservice.domain.Wishlist;
import com.ecommerce.userservice.exception.UnauthorizedAccessException;
import com.ecommerce.userservice.exception.UserNotFoundException;
import com.ecommerce.userservice.exception.WishlistItemNotFoundException;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WishlistService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WishlistService")
class WishlistServiceTest {

    @Mock
    private WishlistRepository wishlistRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WishlistService wishlistService;

    private User caller;

    @BeforeEach
    void setUp() {
        caller = User.builder()
                .id(42L)
                .auth0Id("auth0|caller")
                .email("caller@example.com")
                .role(UserRole.USER)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("should_returnEmptyList_when_userHasNoWishlistItems")
    void should_returnEmptyList_when_userHasNoWishlistItems() {
        when(userRepository.findByAuth0Id("auth0|caller")).thenReturn(Optional.of(caller));
        when(wishlistRepository.findByUserIdOrderByAddedAtDesc(42L)).thenReturn(List.of());

        List<Wishlist> result = wishlistService.getWishlist(42L, "auth0|caller");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should_returnItems_when_userHasWishlistItems")
    void should_returnItems_when_userHasWishlistItems() {
        Wishlist entry = Wishlist.builder().id(1L).userId(42L).productId("p-1").addedAt(Instant.now()).build();
        when(userRepository.findByAuth0Id("auth0|caller")).thenReturn(Optional.of(caller));
        when(wishlistRepository.findByUserIdOrderByAddedAtDesc(42L)).thenReturn(List.of(entry));

        List<Wishlist> result = wishlistService.getWishlist(42L, "auth0|caller");

        assertThat(result).containsExactly(entry);
    }

    @Test
    @DisplayName("should_throwUnauthorized_when_callerJwtSubDoesNotMatchPathUserId")
    void should_throwUnauthorized_when_callerJwtSubDoesNotMatchPathUserId() {
        // caller is user 42, but path requests user 99 (different user's wishlist)
        when(userRepository.findByAuth0Id("auth0|caller")).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> wishlistService.getWishlist(99L, "auth0|caller"))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("not authorized");

        verify(wishlistRepository, never()).findByUserIdOrderByAddedAtDesc(any());
    }

    @Test
    @DisplayName("should_throwUserNotFound_when_callerHasNoUserRecord")
    void should_throwUserNotFound_when_callerHasNoUserRecord() {
        when(userRepository.findByAuth0Id("auth0|ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> wishlistService.getWishlist(42L, "auth0|ghost"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("should_throwUnauthorized_when_callerAuth0IdIsBlank")
    void should_throwUnauthorized_when_callerAuth0IdIsBlank() {
        assertThatThrownBy(() -> wishlistService.getWishlist(42L, ""))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    @DisplayName("should_persistNewItem_when_addingItemToEmptyWishlist")
    void should_persistNewItem_when_addingItemToEmptyWishlist() {
        when(userRepository.findByAuth0Id("auth0|caller")).thenReturn(Optional.of(caller));
        when(wishlistRepository.findByUserIdAndProductId(42L, "p-1")).thenReturn(Optional.empty());
        ArgumentCaptor<Wishlist> captor = ArgumentCaptor.forClass(Wishlist.class);
        when(wishlistRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        Wishlist result = wishlistService.addItem(42L, "p-1", "auth0|caller");

        assertThat(result.getProductId()).isEqualTo("p-1");
        assertThat(result.getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getAddedAt()).isNotNull();
    }

    @Test
    @DisplayName("should_returnExistingEntry_when_productAlreadyOnWishlist")
    void should_returnExistingEntry_when_productAlreadyOnWishlist() {
        Wishlist existing = Wishlist.builder().id(7L).userId(42L).productId("p-1").addedAt(Instant.now()).build();
        when(userRepository.findByAuth0Id("auth0|caller")).thenReturn(Optional.of(caller));
        when(wishlistRepository.findByUserIdAndProductId(42L, "p-1")).thenReturn(Optional.of(existing));

        Wishlist result = wishlistService.addItem(42L, "p-1", "auth0|caller");

        assertThat(result).isSameAs(existing);
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("should_throwUnauthorized_when_addingItemToAnotherUsersWishlist")
    void should_throwUnauthorized_when_addingItemToAnotherUsersWishlist() {
        when(userRepository.findByAuth0Id("auth0|caller")).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> wishlistService.addItem(99L, "p-1", "auth0|caller"))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(wishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("should_deleteItem_when_removingExistingProduct")
    void should_deleteItem_when_removingExistingProduct() {
        when(userRepository.findByAuth0Id("auth0|caller")).thenReturn(Optional.of(caller));
        when(wishlistRepository.existsByUserIdAndProductId(42L, "p-1")).thenReturn(true);

        wishlistService.removeItem(42L, "p-1", "auth0|caller");

        verify(wishlistRepository, times(1)).deleteByUserIdAndProductId(42L, "p-1");
    }

    @Test
    @DisplayName("should_throwWishlistItemNotFound_when_removingMissingProduct")
    void should_throwWishlistItemNotFound_when_removingMissingProduct() {
        when(userRepository.findByAuth0Id("auth0|caller")).thenReturn(Optional.of(caller));
        when(wishlistRepository.existsByUserIdAndProductId(42L, "missing")).thenReturn(false);

        assertThatThrownBy(() -> wishlistService.removeItem(42L, "missing", "auth0|caller"))
                .isInstanceOf(WishlistItemNotFoundException.class);

        verify(wishlistRepository, never()).deleteByUserIdAndProductId(any(), any());
    }

    @Test
    @DisplayName("should_throwUnauthorized_when_removingItemFromAnotherUsersWishlist")
    void should_throwUnauthorized_when_removingItemFromAnotherUsersWishlist() {
        when(userRepository.findByAuth0Id("auth0|caller")).thenReturn(Optional.of(caller));

        assertThatThrownBy(() -> wishlistService.removeItem(99L, "p-1", "auth0|caller"))
                .isInstanceOf(UnauthorizedAccessException.class);

        verify(wishlistRepository, never()).deleteByUserIdAndProductId(any(), any());
    }
}
