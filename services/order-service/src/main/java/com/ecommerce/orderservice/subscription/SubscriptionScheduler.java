package com.ecommerce.orderservice.subscription;

import com.ecommerce.orderservice.client.ProductPriceClient;
import com.ecommerce.orderservice.domain.embedded.Address;
import com.ecommerce.orderservice.domain.entity.OrderItem;
import com.ecommerce.orderservice.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls the {@code subscriptions} table at a fixed interval and fires due
 * subscriptions through {@link OrderService} (§3.6).
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
    private final OrderService orderService;
    private final ProductPriceClient productPriceClient;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean enabled;

    /**
     * Last-resort price used only when product-service cannot be reached AND
     * no per-subscription price is stored. Keeps the order non-zero so the
     * Order entity's "total &gt; 0" invariant holds and the customer is billed
     * at a known floor rather than skipped.
     */
    private final BigDecimal fallbackPrice;

    public SubscriptionScheduler(
        SubscriptionRepository subscriptionRepository,
        OrderService orderService,
        ProductPriceClient productPriceClient,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${subscription.scheduler-enabled:true}") boolean enabled,
        @Value("${subscription.fallback-price:0.01}") BigDecimal fallbackPrice
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.orderService = orderService;
        this.productPriceClient = productPriceClient;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.enabled = enabled;
        this.fallbackPrice = fallbackPrice;
    }

    @Scheduled(fixedDelayString = "${subscription.scheduler-poll-ms:60000}")
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
     */
    @Transactional
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
                fireOrder(sub);
                advance(sub, now);
                processed++;
            } catch (RuntimeException ex) {
                // One bad row should not block the rest — log and move on. The
                // scheduler will retry next poll because next_run_at is
                // unchanged.
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

    private void fireOrder(Subscription sub) {
        Address address = parseAddress(sub.getShippingAddressJson());
        BigDecimal price = productPriceClient.getCurrentPrice(sub.getProductId())
            .orElseGet(() -> {
                log.warn("No current price for product {} (subscription {}) — using fallback {}",
                    sub.getProductId(), sub.getId(), fallbackPrice);
                return fallbackPrice;
            });
        OrderItem item = OrderItem.builder()
            .productId(sub.getProductId())
            .productName("Subscription product " + sub.getProductId())
            .price(price)
            .quantity(sub.getQuantity())
            .build();
        orderService.createOrder(sub.getUserId(), List.of(item), address, null);
    }

    private Address parseAddress(String json) {
        try {
            return objectMapper.readValue(json, Address.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                "Subscription has malformed shippingAddressJson: " + ex.getMessage(), ex);
        }
    }

    private void advance(Subscription sub, LocalDateTime now) {
        sub.setLastRunAt(now);
        sub.setNextRunAt(sub.getNextRunAt().plusDays(sub.getIntervalDays()));
        subscriptionRepository.save(sub);
    }
}
