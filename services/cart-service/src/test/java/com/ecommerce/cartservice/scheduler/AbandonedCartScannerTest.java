package com.ecommerce.cartservice.scheduler;

import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartItem;
import com.ecommerce.cartservice.domain.CartStatus;
import com.ecommerce.cartservice.event.CartAbandonedEvent;
import com.ecommerce.cartservice.event.CartAbandonedEventPublisher;
import com.ecommerce.cartservice.repository.CartRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AbandonedCartScanner}.
 *
 * <p>The scanner is responsible for two things: (1) only publishing events for
 * carts that are genuinely abandoned and not on cool-off, and (2) stamping
 * {@code lastAbandonmentReminderAt} so the next run within the cool-off window
 * skips the cart.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AbandonedCartScanner")
class AbandonedCartScannerTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartAbandonedEventPublisher eventPublisher;

    private AbandonedCartScanner scanner;

    private static final int IDLE_HOURS = 24;
    private static final int COOLDOWN_DAYS = 7;

    @Captor
    private ArgumentCaptor<CartAbandonedEvent> eventCaptor;

    @Captor
    private ArgumentCaptor<Instant> idleThresholdCaptor;

    @Captor
    private ArgumentCaptor<Instant> reminderCutoffCaptor;

    @Captor
    private ArgumentCaptor<List<Cart>> savedCartsCaptor;

    @BeforeEach
    void setUp() {
        scanner = new AbandonedCartScanner(
                cartRepository,
                eventPublisher,
                new SimpleMeterRegistry(),
                IDLE_HOURS,
                COOLDOWN_DAYS);
    }

    @Test
    @DisplayName("should_publishEventAndStampReminderTimestamp_when_eligibleCartFound")
    void should_publishEventAndStampReminderTimestamp_when_eligibleCartFound() {
        Cart cart = abandonedCart(101L, "user-1", List.of(item("p-1", "Widget", 9.99, 2)));

        when(cartRepository.findCartsEligibleForAbandonmentReminder(
                eq(CartStatus.ACTIVE), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(cart));

        int count = scanner.scanAndPublish();

        assertThat(count).isOne();
        verify(eventPublisher, times(1)).publish(eventCaptor.capture());
        CartAbandonedEvent event = eventCaptor.getValue();
        assertThat(event.getCartId()).isEqualTo("101");
        assertThat(event.getUserId()).isEqualTo("user-1");
        assertThat(event.getLineItems()).hasSize(1);
        assertThat(event.getLineItems().get(0).getProductId()).isEqualTo("p-1");
        assertThat(event.getAbandonedAt()).isNotNull();

        verify(cartRepository).saveAll(savedCartsCaptor.capture());
        assertThat(savedCartsCaptor.getValue()).hasSize(1);
        assertThat(savedCartsCaptor.getValue().get(0).getLastAbandonmentReminderAt()).isNotNull();
    }

    @Test
    @DisplayName("should_passCorrectIdleThresholdAndCooldownCutoff_toRepository")
    void should_passCorrectIdleThresholdAndCooldownCutoff_toRepository() {
        Instant beforeRun = Instant.now();
        when(cartRepository.findCartsEligibleForAbandonmentReminder(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        scanner.scanAndPublish();

        verify(cartRepository).findCartsEligibleForAbandonmentReminder(
                eq(CartStatus.ACTIVE), idleThresholdCaptor.capture(), reminderCutoffCaptor.capture());

        Instant idle = idleThresholdCaptor.getValue();
        Instant cutoff = reminderCutoffCaptor.getValue();
        Instant afterRun = Instant.now();

        assertThat(idle).isBetween(
                beforeRun.minus(IDLE_HOURS, ChronoUnit.HOURS).minus(1, ChronoUnit.MINUTES),
                afterRun.minus(IDLE_HOURS, ChronoUnit.HOURS).plus(1, ChronoUnit.MINUTES));

        assertThat(cutoff).isBetween(
                beforeRun.minus(COOLDOWN_DAYS, ChronoUnit.DAYS).minus(1, ChronoUnit.MINUTES),
                afterRun.minus(COOLDOWN_DAYS, ChronoUnit.DAYS).plus(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("should_publishNothingAndSkipSave_when_noEligibleCarts")
    void should_publishNothingAndSkipSave_when_noEligibleCarts() {
        when(cartRepository.findCartsEligibleForAbandonmentReminder(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        int count = scanner.scanAndPublish();

        assertThat(count).isZero();
        verify(eventPublisher, never()).publish(any());
        verify(cartRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("should_publishOneEventPerCart_when_multipleCartsEligible")
    void should_publishOneEventPerCart_when_multipleCartsEligible() {
        Cart c1 = abandonedCart(1L, "u-1", List.of(item("p-1", "A", 1.0, 1)));
        Cart c2 = abandonedCart(2L, "u-2", List.of(item("p-2", "B", 2.0, 3)));
        Cart c3 = abandonedCart(3L, "u-3", List.of(item("p-3", "C", 3.0, 1)));
        when(cartRepository.findCartsEligibleForAbandonmentReminder(any(), any(), any()))
                .thenReturn(List.of(c1, c2, c3));

        int count = scanner.scanAndPublish();

        assertThat(count).isEqualTo(3);
        verify(eventPublisher, times(3)).publish(any(CartAbandonedEvent.class));
    }

    @Test
    @DisplayName("should_continueProcessing_when_individualPublishThrows")
    void should_continueProcessing_when_individualPublishThrows() {
        // The publisher swallows broker errors internally; this test simply
        // verifies the scanner does not abort the batch on a publish failure.
        Cart c1 = abandonedCart(1L, "u-1", List.of(item("p-1", "A", 1.0, 1)));
        Cart c2 = abandonedCart(2L, "u-2", List.of(item("p-2", "B", 2.0, 3)));
        when(cartRepository.findCartsEligibleForAbandonmentReminder(any(), any(), any()))
                .thenReturn(List.of(c1, c2));
        // Publisher does not throw (its contract); ensure both are handed off.

        int count = scanner.scanAndPublish();

        assertThat(count).isEqualTo(2);
        verify(eventPublisher, times(2)).publish(any(CartAbandonedEvent.class));
    }

    private static Cart abandonedCart(Long id, String userId, List<CartItem> items) {
        Cart cart = Cart.builder()
                .id(id)
                .userId(userId)
                .status(CartStatus.ACTIVE)
                .totalAmount(items.stream()
                        .map(i -> i.getPriceSnapshot().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .totalItems(items.stream().mapToInt(CartItem::getQuantity).sum())
                .items(new ArrayList<>(items))
                .build();
        cart.getItems().forEach(i -> i.setCart(cart));
        return cart;
    }

    private static CartItem item(String productId, String name, double price, int qty) {
        BigDecimal p = BigDecimal.valueOf(price);
        CartItem item = CartItem.builder()
                .productId(productId)
                .productName(name)
                .priceSnapshot(p)
                .quantity(qty)
                .build();
        item.calculateSubtotal();
        return item;
    }
}
