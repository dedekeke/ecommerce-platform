package com.ecommerce.cartservice.scheduler;

import com.ecommerce.cartservice.domain.Cart;
import com.ecommerce.cartservice.domain.CartStatus;
import com.ecommerce.cartservice.repository.CartRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@Slf4j
public class CartCleanupScheduledJob {

    private final CartRepository cartRepository;
    private final Counter expiredCartsCounter;
    private final Counter abandonedCartsCounter;
    private final int abandonedThresholdHours;

    public CartCleanupScheduledJob(
            CartRepository cartRepository,
            MeterRegistry meterRegistry,
            @Value("${cart.cleanup.abandoned-threshold-hours:24}") int abandonedThresholdHours) {
        this.cartRepository = cartRepository;
        this.abandonedThresholdHours = abandonedThresholdHours;
        this.expiredCartsCounter = Counter.builder("cart.cleanup.expired")
                .description("Number of expired carts deleted")
                .register(meterRegistry);
        this.abandonedCartsCounter = Counter.builder("cart.cleanup.abandoned")
                .description("Number of carts marked as abandoned")
                .register(meterRegistry);
    }

    @Scheduled(cron = "${cart.cleanup.expired-cron:0 0 2 * * ?}")
    @SchedulerLock(name = "cart-cleanupExpiredCarts",
        lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void cleanupExpiredCarts() {
        log.info("Starting expired cart cleanup job");
        long startTime = System.currentTimeMillis();

        try {
            Instant now = Instant.now();
            List<Cart> expiredCarts = cartRepository.findExpiredCarts(now, CartStatus.ACTIVE);

            if (!expiredCarts.isEmpty()) {
                log.info("Found {} expired carts to delete", expiredCarts.size());
                cartRepository.deleteAll(expiredCarts);
                expiredCartsCounter.increment(expiredCarts.size());
                log.info("Deleted {} expired carts", expiredCarts.size());
            } else {
                log.debug("No expired carts found");
            }

        } catch (Exception e) {
            log.error("Error during expired cart cleanup: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Expired cart cleanup job completed in {} ms", duration);
    }

    @Scheduled(cron = "${cart.cleanup.abandoned-cron:0 0 3 * * ?}")
    @SchedulerLock(name = "cart-processAbandonedCarts",
        lockAtMostFor = "PT15M", lockAtLeastFor = "PT1M")
    public void processAbandonedCarts() {
        log.info("Starting abandoned cart processing job");
        long startTime = System.currentTimeMillis();

        try {
            Instant threshold = Instant.now().minus(abandonedThresholdHours, ChronoUnit.HOURS);
            List<Cart> abandonedCarts = cartRepository.findAbandonedCarts(threshold, CartStatus.ACTIVE);

            if (!abandonedCarts.isEmpty()) {
                log.info("Found {} abandoned carts to process", abandonedCarts.size());

                abandonedCarts.forEach(Cart::markAsAbandoned);
                cartRepository.saveAll(abandonedCarts);
                abandonedCartsCounter.increment(abandonedCarts.size());

                log.info("Marked {} carts as abandoned", abandonedCarts.size());
            } else {
                log.debug("No abandoned carts found");
            }

        } catch (Exception e) {
            log.error("Error during abandoned cart processing: {}", e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Abandoned cart processing job completed in {} ms", duration);
    }
}
