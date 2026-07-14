package com.ecommerce.cartservice.scheduler;

import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartItem;
import com.ecommerce.cartservice.domain.CartStatus;
import com.ecommerce.cartservice.event.CartAbandonedEvent;
import com.ecommerce.cartservice.event.CartAbandonedEventPublisher;
import com.ecommerce.cartservice.repository.CartRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scans for abandoned carts and publishes {@code cart.abandoned} events for
 * the notification-service to pick up (§3.10).
 *
 * <p>Runs daily at 3 AM by default (config: {@code cart.abandonment.scan-cron}).
 * A cart is considered abandoned and email-eligible when:
 * <ul>
 *   <li>status = ACTIVE,</li>
 *   <li>{@code updated_at} is older than {@code cart.abandonment.idle-threshold-hours} (default 24h),</li>
 *   <li>it contains at least one line item, and</li>
 *   <li>no reminder was sent in the last {@code cart.abandonment.reminder-cooldown-days} days (default 7).</li>
 * </ul>
 *
 * <p>The dedup window is enforced both in the SQL query (avoiding pulling
 * obviously ineligible rows) and by stamping {@code last_abandonment_reminder_at}
 * after each successful publish so a re-run within the cool-off skips the cart.</p>
 */
@Component
@Slf4j
public class AbandonedCartScanner {

    private final CartRepository cartRepository;
    private final CartAbandonedEventPublisher eventPublisher;
    private final Counter publishedCounter;
    private final int idleThresholdHours;
    private final int reminderCooldownDays;

    public AbandonedCartScanner(
            CartRepository cartRepository,
            CartAbandonedEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            @Value("${cart.abandonment.idle-threshold-hours:24}") int idleThresholdHours,
            @Value("${cart.abandonment.reminder-cooldown-days:7}") int reminderCooldownDays) {
        this.cartRepository = cartRepository;
        this.eventPublisher = eventPublisher;
        this.idleThresholdHours = idleThresholdHours;
        this.reminderCooldownDays = reminderCooldownDays;
        this.publishedCounter = Counter.builder("cart.abandonment.published")
                .description("Number of cart.abandoned events published")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${cart.abandonment.scan-cron:0 0 3 * * ?}")
    @SchedulerLock(name = "cart-abandonmentScan",
        lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void scan() {
        log.info("Starting abandoned cart scan");
        long startedAt = System.currentTimeMillis();

        try {
            scanAndPublish();
        } catch (Exception ex) {
            log.error("Abandoned cart scan failed", ex);
        }

        long duration = System.currentTimeMillis() - startedAt;
        log.info("Abandoned cart scan finished in {} ms", duration);
    }

    /**
     * Visible for testing — performs the actual scan + publish under a single
     * transaction so reminder timestamps and event publication share the same
     * commit boundary.
     */
    @Transactional
    public int scanAndPublish() {
        Instant now = Instant.now();
        Instant idleThreshold = now.minus(idleThresholdHours, ChronoUnit.HOURS);
        Instant reminderCutoff = now.minus(reminderCooldownDays, ChronoUnit.DAYS);

        List<Cart> eligible = cartRepository.findCartsEligibleForAbandonmentReminder(
                CartStatus.ACTIVE, idleThreshold, reminderCutoff);

        if (eligible.isEmpty()) {
            log.debug("No abandoned carts eligible for a reminder");
            return 0;
        }

        log.info("Found {} abandoned cart(s) eligible for reminders", eligible.size());

        int published = 0;
        for (Cart cart : eligible) {
            CartAbandonedEvent event = toEvent(cart, now);
            eventPublisher.publish(event);
            cart.setLastAbandonmentReminderAt(now);
            published++;
        }

        cartRepository.saveAll(eligible);
        publishedCounter.increment(published);
        return published;
    }

    private CartAbandonedEvent toEvent(Cart cart, Instant abandonedAt) {
        List<CartAbandonedEvent.LineItem> items = cart.getItems().stream()
                .map(AbandonedCartScanner::toLineItem)
                .toList();

        return CartAbandonedEvent.builder()
                .cartId(String.valueOf(cart.getId()))
                .userId(cart.getUserId())
                // userEmail is denormalised onto the cart on first add-to-cart
                // (resolved from user-service). May be null if that lookup
                // failed; notification-service skips events without an email.
                .userEmail(cart.getUserEmail())
                .totalAmount(cart.getTotalAmount())
                .totalItems(cart.getTotalItems())
                .lineItems(items)
                .abandonedAt(abandonedAt)
                .build();
    }

    private static CartAbandonedEvent.LineItem toLineItem(CartItem item) {
        return CartAbandonedEvent.LineItem.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .productImageUrl(item.getProductImageUrl())
                .price(item.getPriceSnapshot())
                .quantity(item.getQuantity())
                .build();
    }
}
