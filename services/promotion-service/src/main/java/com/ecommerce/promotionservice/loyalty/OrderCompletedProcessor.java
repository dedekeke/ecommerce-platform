package com.ecommerce.promotionservice.loyalty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Transactional worker behind {@link OrderCompletedConsumer}.
 *
 * <p>Lives in its own bean so the whole unit runs through the Spring
 * transaction proxy (a self-invoked {@code @Transactional} method on the
 * listener would be bypassed) and so the consumer can catch a rolled-back
 * duplicate-key violation <em>after</em> the transaction has fully rolled back
 * — avoiding the "current transaction is marked rollback-only" trap.
 *
 * <p>Insert-first idempotency: claim the event by INSERTing its
 * {@link ProcessedLoyaltyEvent} row, then accumulate spend, all in one
 * transaction. A duplicate id fails the INSERT ({@code DataIntegrityViolationException})
 * before any spend is applied, and the transaction rolls back — so a redelivery
 * or a concurrent replica can never double-count.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCompletedProcessor {

    private final LoyaltyService loyaltyService;
    private final ProcessedLoyaltyEventRepository processedEventRepository;

    /**
     * Record spend for a single {@code order.completed} event.
     *
     * @param eventId the {@code outbox-event-id}; {@code null} for a
     *                legacy/manual publish with no header (processed without
     *                dedup)
     * @throws org.springframework.dao.DataIntegrityViolationException if
     *         {@code eventId} was already processed — the caller treats this as
     *         an idempotent success
     */
    @Transactional
    public void process(String userId, BigDecimal amount, LocalDateTime occurredAt, String eventId) {
        if (eventId != null) {
            // Insert-first: fails here on a duplicate, before spend is touched.
            processedEventRepository.saveAndFlush(ProcessedLoyaltyEvent.builder()
                    .eventId(eventId)
                    .topic(OrderCompletedConsumer.TOPIC)
                    .consumedAt(Instant.now())
                    .build());
        }
        loyaltyService.recordSpend(userId, amount, occurredAt);
    }
}
