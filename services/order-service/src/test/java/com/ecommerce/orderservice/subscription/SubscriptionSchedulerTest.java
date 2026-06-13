package com.ecommerce.orderservice.subscription;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubscriptionScheduler} (§3.6).
 *
 * <p>The scheduler is now a thin poller: it queries due rows, applies the dedup
 * window, and delegates each row to {@link SubscriptionRunner} (which owns the
 * REQUIRES_NEW transaction + order creation). These tests therefore verify the
 * poll / dedup / batch-resilience contract and mock the runner.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionScheduler")
class SubscriptionSchedulerTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionRunner subscriptionRunner;

    private final Clock fixedClock = Clock.fixed(
        Instant.parse("2026-04-29T10:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.ofInstant(fixedClock.instant(), ZoneOffset.UTC);

    private SubscriptionScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new SubscriptionScheduler(subscriptionRepository, subscriptionRunner,
            fixedClock, true);
    }

    @Test
    @DisplayName("should_delegateToRunnerAndCount_when_subscriptionDue")
    void should_delegateToRunnerAndCount_when_subscriptionDue() {
        Subscription due = dueSubscription(1L, 7, null);
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(due));

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isEqualTo(1);
        verify(subscriptionRunner, times(1)).fireAndAdvance(due, now);
    }

    @Test
    @DisplayName("should_skipSubscription_when_lastRunWithinDedupWindow")
    void should_skipSubscription_when_lastRunWithinDedupWindow() {
        // Last run 30 minutes ago — inside the 1-hour dedup window.
        Subscription due = dueSubscription(1L, 7, now.minusMinutes(30));
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(due));

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isZero();
        verify(subscriptionRunner, never()).fireAndAdvance(any(), any());
    }

    @Test
    @DisplayName("should_processSubscription_when_lastRunOutsideDedupWindow")
    void should_processSubscription_when_lastRunOutsideDedupWindow() {
        // Last run 2 hours ago — outside the 1-hour dedup window.
        Subscription due = dueSubscription(1L, 7, now.minusHours(2));
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(due));

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isEqualTo(1);
        verify(subscriptionRunner, times(1)).fireAndAdvance(due, now);
    }

    @Test
    @DisplayName("should_continueProcessing_when_oneSubscriptionFails")
    void should_continueProcessing_when_oneSubscriptionFails() {
        Subscription bad = dueSubscription(1L, 7, null);
        Subscription good = dueSubscription(2L, 7, null);
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of(bad, good));
        doThrow(new RuntimeException("create failed")).when(subscriptionRunner).fireAndAdvance(eq(bad), any());

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isEqualTo(1);
        verify(subscriptionRunner, times(1)).fireAndAdvance(good, now);
    }

    @Test
    @DisplayName("poll_should_beNoOp_when_disabled")
    void poll_should_beNoOp_when_disabled() {
        SubscriptionScheduler disabled = new SubscriptionScheduler(
            subscriptionRepository, subscriptionRunner, fixedClock, false);

        disabled.poll();

        verify(subscriptionRepository, never()).findByStatusAndNextRunAtBefore(any(), any());
    }

    @Test
    @DisplayName("processDueSubscriptions_should_returnZero_when_noDueRows")
    void processDueSubscriptions_should_returnZero_when_noDueRows() {
        when(subscriptionRepository.findByStatusAndNextRunAtBefore(SubscriptionStatus.ACTIVE, now))
            .thenReturn(List.of());

        int processed = scheduler.processDueSubscriptions();

        assertThat(processed).isZero();
        verify(subscriptionRunner, never()).fireAndAdvance(any(), any());
    }

    private Subscription dueSubscription(long id, int intervalDays, LocalDateTime lastRunAt) {
        return Subscription.builder()
            .id(id)
            .userId("user-1")
            .productId("prod-1")
            .quantity(1)
            .intervalDays(intervalDays)
            .shippingAddressJson("{}")
            .status(SubscriptionStatus.ACTIVE)
            .nextRunAt(now.minusHours(1))
            .lastRunAt(lastRunAt)
            .build();
    }
}
