package com.ecommerce.orderservice.subscription;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Subscription / recurring-order service (§3.6).
 *
 * <p>Owns CRUD + state transitions for {@link Subscription} entities. The
 * actual order creation is delegated to {@link SubscriptionScheduler}; this
 * service is the source of truth for the lifecycle (ACTIVE / PAUSED /
 * CANCELLED) and the scheduling cursor ({@code nextRunAt}).</p>
 *
 * <p>State transition rules:
 * <ul>
 *   <li>PAUSED  → ACTIVE  (resume)</li>
 *   <li>ACTIVE  → PAUSED  (pause)</li>
 *   <li>any    → CANCELLED (cancel — terminal)</li>
 * </ul>
 * Cancellation is terminal: cancelled rows are kept for history but never
 * resumed (callers must create a new subscription instead).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    @Transactional
    public Subscription create(SubscriptionDtos.CreateSubscriptionRequest req) {
        LocalDateTime now = LocalDateTime.now(clock);
        Subscription sub = Subscription.builder()
            .userId(req.userId())
            .productId(req.productId())
            .quantity(req.quantity())
            .intervalDays(req.intervalDays())
            .paymentMethodId(req.paymentMethodId())
            .shippingAddressJson(req.shippingAddressJson())
            .status(SubscriptionStatus.ACTIVE)
            .nextRunAt(now.plusDays(req.intervalDays()))
            .build();
        Subscription saved = subscriptionRepository.save(sub);
        log.info("Subscription created: id={} userId={} productId={} intervalDays={}",
            saved.getId(), saved.getUserId(), saved.getProductId(), saved.getIntervalDays());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Subscription> listForUser(String userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    @Transactional
    public Subscription pause(Long id) {
        Subscription sub = require(id);
        if (sub.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot pause a cancelled subscription: " + id);
        }
        sub.setStatus(SubscriptionStatus.PAUSED);
        log.info("Subscription paused: id={}", id);
        return sub;
    }

    @Transactional
    public Subscription resume(Long id) {
        Subscription sub = require(id);
        if (sub.getStatus() == SubscriptionStatus.CANCELLED) {
            throw new IllegalStateException("Cannot resume a cancelled subscription: " + id);
        }
        sub.setStatus(SubscriptionStatus.ACTIVE);
        log.info("Subscription resumed: id={}", id);
        return sub;
    }

    @Transactional
    public Subscription cancel(Long id) {
        Subscription sub = require(id);
        sub.setStatus(SubscriptionStatus.CANCELLED);
        log.info("Subscription cancelled: id={}", id);
        return sub;
    }

    private Subscription require(Long id) {
        return subscriptionRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + id));
    }
}
