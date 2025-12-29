package com.ecommerce.cartservice.scheduler;

import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartStatus;
import com.ecommerce.cartservice.repository.CartRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Cart Cleanup Scheduled Job Tests")
class CartCleanupScheduledJobTest {

    @Mock
    private CartRepository cartRepository;

    private CartCleanupScheduledJob cartCleanupScheduledJob;

    private static final int ABANDONED_THRESHOLD_HOURS = 24;

    @BeforeEach
    void setUp() {
        cartCleanupScheduledJob = new CartCleanupScheduledJob(
                cartRepository,
                new SimpleMeterRegistry(),
                ABANDONED_THRESHOLD_HOURS
        );
    }

    @Captor
    private ArgumentCaptor<Instant> instantCaptor;

    @Captor
    private ArgumentCaptor<List<Cart>> cartsCaptor;

    private Cart createCart(Long id, String userId, CartStatus status) {
        return Cart.builder()
                .id(id)
                .userId(userId)
                .status(status)
                .totalAmount(BigDecimal.ZERO)
                .totalItems(0)
                .build();
    }

    @Nested
    @DisplayName("Expired Cart Cleanup Tests")
    class ExpiredCartCleanupTests {

        @Test
        @DisplayName("Should delete expired carts when found")
        void shouldDeleteExpiredCartsWhenFound() {
            // Given
            Cart expiredCart1 = createCart(1L, "user1", CartStatus.ACTIVE);
            Cart expiredCart2 = createCart(2L, "user2", CartStatus.ACTIVE);
            List<Cart> expiredCarts = Arrays.asList(expiredCart1, expiredCart2);

            when(cartRepository.findExpiredCarts(any(Instant.class), eq(CartStatus.ACTIVE)))
                    .thenReturn(expiredCarts);

            // When
            cartCleanupScheduledJob.cleanupExpiredCarts();

            // Then
            verify(cartRepository).findExpiredCarts(any(Instant.class), eq(CartStatus.ACTIVE));
            verify(cartRepository).deleteAll(cartsCaptor.capture());
            assertThat(cartsCaptor.getValue()).hasSize(2);
        }

        @Test
        @DisplayName("Should not delete anything when no expired carts found")
        void shouldNotDeleteWhenNoExpiredCartsFound() {
            // Given
            when(cartRepository.findExpiredCarts(any(Instant.class), eq(CartStatus.ACTIVE)))
                    .thenReturn(Collections.emptyList());

            // When
            cartCleanupScheduledJob.cleanupExpiredCarts();

            // Then
            verify(cartRepository).findExpiredCarts(any(Instant.class), eq(CartStatus.ACTIVE));
            verify(cartRepository, never()).deleteAll(anyList());
        }

        @Test
        @DisplayName("Should handle exception gracefully during cleanup")
        void shouldHandleExceptionGracefully() {
            // Given
            when(cartRepository.findExpiredCarts(any(Instant.class), eq(CartStatus.ACTIVE)))
                    .thenThrow(new RuntimeException("Database error"));

            // When - should not throw
            cartCleanupScheduledJob.cleanupExpiredCarts();

            // Then - verify it was called but didn't propagate exception
            verify(cartRepository).findExpiredCarts(any(Instant.class), eq(CartStatus.ACTIVE));
        }
    }

    @Nested
    @DisplayName("Abandoned Cart Processing Tests")
    class AbandonedCartProcessingTests {

        @Test
        @DisplayName("Should mark carts as abandoned when inactive for threshold period")
        void shouldMarkCartsAsAbandonedWhenInactive() {
            // Given
            Cart abandonedCart1 = createCart(1L, "user1", CartStatus.ACTIVE);
            Cart abandonedCart2 = createCart(2L, "user2", CartStatus.ACTIVE);
            List<Cart> abandonedCarts = Arrays.asList(abandonedCart1, abandonedCart2);

            when(cartRepository.findAbandonedCarts(any(Instant.class), eq(CartStatus.ACTIVE)))
                    .thenReturn(abandonedCarts);
            when(cartRepository.saveAll(anyList())).thenReturn(abandonedCarts);

            // When
            cartCleanupScheduledJob.processAbandonedCarts();

            // Then
            verify(cartRepository).findAbandonedCarts(instantCaptor.capture(), eq(CartStatus.ACTIVE));
            verify(cartRepository).saveAll(cartsCaptor.capture());

            List<Cart> savedCarts = cartsCaptor.getValue();
            assertThat(savedCarts).hasSize(2);
            assertThat(savedCarts).allMatch(cart -> cart.getStatus() == CartStatus.ABANDONED);
        }

        @Test
        @DisplayName("Should not save anything when no abandoned carts found")
        void shouldNotSaveWhenNoAbandonedCartsFound() {
            // Given
            when(cartRepository.findAbandonedCarts(any(Instant.class), eq(CartStatus.ACTIVE)))
                    .thenReturn(Collections.emptyList());

            // When
            cartCleanupScheduledJob.processAbandonedCarts();

            // Then
            verify(cartRepository).findAbandonedCarts(any(Instant.class), eq(CartStatus.ACTIVE));
            verify(cartRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("Should use correct threshold time for abandoned cart detection")
        void shouldUseCorrectThresholdTimeForAbandonedCarts() {
            // Given
            when(cartRepository.findAbandonedCarts(any(Instant.class), eq(CartStatus.ACTIVE)))
                    .thenReturn(Collections.emptyList());

            Instant beforeExecution = Instant.now().minus(24, ChronoUnit.HOURS);

            // When
            cartCleanupScheduledJob.processAbandonedCarts();

            // Then
            verify(cartRepository).findAbandonedCarts(instantCaptor.capture(), eq(CartStatus.ACTIVE));
            Instant capturedThreshold = instantCaptor.getValue();

            // Threshold should be approximately 24 hours ago (within 1 minute tolerance)
            assertThat(capturedThreshold).isBetween(
                    beforeExecution.minus(1, ChronoUnit.MINUTES),
                    Instant.now().minus(24, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES)
            );
        }

        @Test
        @DisplayName("Should handle exception gracefully during abandoned cart processing")
        void shouldHandleExceptionGracefully() {
            // Given
            when(cartRepository.findAbandonedCarts(any(Instant.class), eq(CartStatus.ACTIVE)))
                    .thenThrow(new RuntimeException("Database error"));

            // When - should not throw
            cartCleanupScheduledJob.processAbandonedCarts();

            // Then - verify it was called but didn't propagate exception
            verify(cartRepository).findAbandonedCarts(any(Instant.class), eq(CartStatus.ACTIVE));
        }
    }

    @Nested
    @DisplayName("Job Execution Tests")
    class JobExecutionTests {

        @Test
        @DisplayName("Should log metrics after cleanup")
        void shouldProcessCartsCorrectly() {
            // Given
            Cart expiredCart = createCart(1L, "user1", CartStatus.ACTIVE);
            Cart abandonedCart = createCart(2L, "user2", CartStatus.ACTIVE);

            when(cartRepository.findExpiredCarts(any(Instant.class), eq(CartStatus.ACTIVE)))
                    .thenReturn(Collections.singletonList(expiredCart));
            when(cartRepository.findAbandonedCarts(any(Instant.class), eq(CartStatus.ACTIVE)))
                    .thenReturn(Collections.singletonList(abandonedCart));
            when(cartRepository.saveAll(anyList())).thenReturn(Collections.singletonList(abandonedCart));

            // When
            cartCleanupScheduledJob.cleanupExpiredCarts();
            cartCleanupScheduledJob.processAbandonedCarts();

            // Then
            verify(cartRepository).deleteAll(anyList());
            verify(cartRepository).saveAll(anyList());
        }
    }
}
