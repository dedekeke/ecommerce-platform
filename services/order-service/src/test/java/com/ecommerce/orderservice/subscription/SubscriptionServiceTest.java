package com.ecommerce.orderservice.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubscriptionService} — CRUD + state transitions.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionService")
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    private SubscriptionService subscriptionService;

    private final Clock fixedClock = Clock.fixed(
        Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(subscriptionRepository, fixedClock);
    }

    @Test
    @DisplayName("should_createActiveSubscription_when_validRequest")
    void should_createActiveSubscription_when_validRequest() {
        SubscriptionDtos.CreateSubscriptionRequest req = new SubscriptionDtos.CreateSubscriptionRequest(
            "user-1", "prod-1", 2, 7, "pm-1", "{}");
        when(subscriptionRepository.save(any(Subscription.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        Subscription created = subscriptionService.create(req);

        assertThat(created.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(created.getUserId()).isEqualTo("user-1");
        assertThat(created.getProductId()).isEqualTo("prod-1");
        assertThat(created.getQuantity()).isEqualTo(2);
        assertThat(created.getIntervalDays()).isEqualTo(7);
        assertThat(created.getNextRunAt())
            .isEqualTo(LocalDateTime.of(2026, 5, 6, 10, 0));
    }

    @Test
    @DisplayName("listForUser_should_returnRepoResult")
    void listForUser_should_returnRepoResult() {
        Subscription sub = activeSubscription(1L);
        when(subscriptionRepository.findByUserId("user-1")).thenReturn(List.of(sub));

        List<Subscription> result = subscriptionService.listForUser("user-1");

        assertThat(result).containsExactly(sub);
    }

    @Test
    @DisplayName("pause_should_transitionActiveToPaused")
    void pause_should_transitionActiveToPaused() {
        Subscription sub = activeSubscription(1L);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));

        Subscription paused = subscriptionService.pause(1L);

        assertThat(paused.getStatus()).isEqualTo(SubscriptionStatus.PAUSED);
    }

    @Test
    @DisplayName("resume_should_transitionPausedToActive")
    void resume_should_transitionPausedToActive() {
        Subscription sub = activeSubscription(1L);
        sub.setStatus(SubscriptionStatus.PAUSED);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));

        Subscription resumed = subscriptionService.resume(1L);

        assertThat(resumed.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("cancel_should_transitionToCancelled_fromAnyState")
    void cancel_should_transitionToCancelled_fromAnyState() {
        Subscription sub = activeSubscription(1L);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));

        Subscription cancelled = subscriptionService.cancel(1L);

        assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
    }

    @Test
    @DisplayName("pause_should_throw_when_alreadyCancelled")
    void pause_should_throw_when_alreadyCancelled() {
        Subscription sub = activeSubscription(1L);
        sub.setStatus(SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> subscriptionService.pause(1L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("resume_should_throw_when_alreadyCancelled")
    void resume_should_throw_when_alreadyCancelled() {
        Subscription sub = activeSubscription(1L);
        sub.setStatus(SubscriptionStatus.CANCELLED);
        when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));

        assertThatThrownBy(() -> subscriptionService.resume(1L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("require_should_throw_when_idMissing")
    void require_should_throw_when_idMissing() {
        when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> subscriptionService.cancel(99L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private Subscription activeSubscription(long id) {
        return Subscription.builder()
            .id(id)
            .userId("user-1")
            .productId("prod-1")
            .quantity(1)
            .intervalDays(7)
            .shippingAddressJson("{}")
            .status(SubscriptionStatus.ACTIVE)
            .nextRunAt(LocalDateTime.of(2026, 5, 6, 10, 0))
            .build();
    }
}
