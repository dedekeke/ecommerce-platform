package com.ecommerce.orderservice.subscription;

import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls the {@code subscriptions} table at a fixed interval and fires each due
 * subscription through {@link SubscriptionRunner} — which wraps the order
 * creation in its own {@code REQUIRES_NEW} transaction (§3.6).
 *
 * <p>Idempotency safeguard: a subscription whose {@code lastRunAt} is within
 * {@link #DEDUP_WINDOW} of "now" is skipped. This protects against the
 * scheduler firing twice on a single window in the rare case where (a) the
 * poll interval becomes shorter than the work duration, or (b) the deployment
 * is rolled while a poll is mid-flight. The 1-hour window is large enough to
 * absorb any realistic restart skew and far smaller than the smallest
 * meaningful {@code intervalDays} (1 day).</p>
 *
 * <p>The poll interval is configurable via
 * {@code subscription.scheduler-poll-ms} (default 60000ms / 1 minute) so
 * tests can drive it tighter and ops can tune it without redeploying.</p>
 */
@Slf4j
@Component
public class SubscriptionScheduler {

    /**
     * Skip-window: re-firing a subscription whose lastRunAt is within this
     * duration is treated as a duplicate poll and ignored.
     */
    static final Duration DEDUP_WINDOW = Duration.ofHours(1);

    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionRunner subscriptionRunner;
    private final Clock clock;
    private final boolean enabled;

    public SubscriptionScheduler(
        SubscriptionRepository subscriptionRepository,
        SubscriptionRunner subscriptionRunner,
        Clock clock,
        @Value("${subscription.scheduler-enabled:true}") boolean enabled
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionRunner = subscriptionRunner;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${subscription.scheduler-poll-ms:60000}")
    @SchedulerLock(name = "order-subscriptionPoll",
        lockAtMostFor = "PT5M", lockAtLeastFor = "PT0S")
    public void poll() {
        if (!enabled) {
            log.debug("Subscription scheduler disabled — skipping poll");
            return;
        }
        try {
            int processed = processDueSubscriptions();
            if (processed > 0) {
                log.info("Subscription scheduler processed {} due subscription(s)", processed);
            }
        } catch (RuntimeException ex) {
            // Never let a scheduler crash propagate — just log and let the
            // next poll retry. Per-row failures are caught inside the loop.
            log.error("Subscription scheduler poll failed: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Visible for testability — drains all due subscriptions in one pass and
     * returns the count successfully processed.
     *
     * <p>This method is intentionally NOT {@code @Transactional}: each
     * subscription is fired in its own {@code REQUIRES_NEW} transaction via
     * {@link SubscriptionRunner#fireAndAdvance}, so one failure rolls back only
     * that row and never poisons the rest of the batch.</p>
     */
    public int processDueSubscriptions() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Subscription> due = subscriptionRepository.findByStatusAndNextRunAtBefore(
            SubscriptionStatus.ACTIVE, now);
        int processed = 0;
        for (Subscription sub : due) {
            if (isWithinDedupWindow(sub, now)) {
                log.warn("Skipping subscription {} — last run {} is within dedup window {}",
                    sub.getId(), sub.getLastRunAt(), DEDUP_WINDOW);
                continue;
            }
            try {
                subscriptionRunner.fireAndAdvance(sub, now);
                processed++;
            } catch (RuntimeException ex) {
                // One bad row should not block the rest — log and move on. Its
                // own transaction already rolled back, leaving next_run_at
                // unchanged so the next poll retries it.
                log.error("Failed to process subscription {}: {}", sub.getId(), ex.getMessage(), ex);
            }
        }
        return processed;
    }

    private boolean isWithinDedupWindow(Subscription sub, LocalDateTime now) {
        if (sub.getLastRunAt() == null) {
            return false;
        }
        Duration sinceLastRun = Duration.between(sub.getLastRunAt(), now);
        return !sinceLastRun.isNegative() && sinceLastRun.compareTo(DEDUP_WINDOW) < 0;
    }
}
