package com.ecommerce.orderservice.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    /**
     * Drives {@link SubscriptionScheduler} — returns subscriptions that are
     * ACTIVE and whose {@code nextRunAt} has already passed.
     */
    List<Subscription> findByStatusAndNextRunAtBefore(SubscriptionStatus status, LocalDateTime cutoff);

    List<Subscription> findByUserId(String userId);
}
