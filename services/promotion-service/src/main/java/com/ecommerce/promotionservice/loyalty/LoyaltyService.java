package com.ecommerce.promotionservice.loyalty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Loyalty service (§3.7).
 *
 * <p>Two responsibilities:
 * <ol>
 *   <li><b>Tier lookup</b> for {@code GET /api/promotions/loyalty/{userId}}:
 *       given a user id, find their lifetime spend, place them in the
 *       correct tier, and report the next-tier threshold.</li>
 *   <li><b>Spend accumulation</b> from {@code order.completed} events:
 *       upserts {@code customer_spend.total_spend += order.totalAmount}.</li>
 * </ol>
 *
 * <p>Tier selection rule: pick the tier with the largest {@code minSpend}
 * that is still {@code <= currentSpend}. Tiers are ordered ascending so the
 * computation is a simple linear scan over a tiny in-memory list (4 rows).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoyaltyService {

    private final LoyaltyTierRepository loyaltyTierRepository;
    private final CustomerSpendRepository customerSpendRepository;

    @Transactional(readOnly = true)
    public LoyaltyResponse getLoyaltyForUser(String userId) {
        BigDecimal spend = customerSpendRepository.findById(userId)
                .map(CustomerSpend::getTotalSpend)
                .orElse(BigDecimal.ZERO);
        return computeResponse(spend);
    }

    /**
     * Visible for testing — pure tier calculation given an arbitrary spend.
     */
    public LoyaltyResponse computeResponse(BigDecimal spend) {
        BigDecimal current = spend == null ? BigDecimal.ZERO : spend;
        List<LoyaltyTier> tiers = loyaltyTierRepository.findAllByOrderByMinSpendAsc();
        if (tiers.isEmpty()) {
            return LoyaltyResponse.builder()
                    .tier("BRONZE")
                    .discountPercent(BigDecimal.ZERO)
                    .currentSpend(current)
                    .build();
        }

        LoyaltyTier currentTier = tiers.get(0);
        LoyaltyTier nextTier = null;
        for (LoyaltyTier tier : tiers) {
            if (current.compareTo(tier.getMinSpend()) >= 0) {
                currentTier = tier;
            } else {
                nextTier = tier;
                break;
            }
        }

        BigDecimal nextThreshold = nextTier != null
                ? nextTier.getMinSpend().subtract(current).max(BigDecimal.ZERO)
                : null;

        return LoyaltyResponse.builder()
                .tier(currentTier.getName())
                .discountPercent(currentTier.getDiscountPercent())
                .currentSpend(current)
                .nextTier(nextTier != null ? nextTier.getName() : null)
                .nextTierAt(nextThreshold)
                .build();
    }

    /**
     * Increment the user's lifetime spend by {@code amount}. Idempotency on
     * {@code orderId} is handled by the consumer layer (see
     * {@link OrderCompletedConsumer}).
     */
    @Transactional
    public CustomerSpend recordSpend(String userId, BigDecimal amount, LocalDateTime occurredAt) {
        if (userId == null || userId.isBlank() || amount == null || amount.signum() <= 0) {
            log.debug("Skipping spend record — userId={}, amount={}", userId, amount);
            return null;
        }
        CustomerSpend record = customerSpendRepository.findById(userId)
                .orElseGet(() -> CustomerSpend.builder()
                        .userId(userId)
                        .totalSpend(BigDecimal.ZERO)
                        .build());
        record.setTotalSpend(record.getTotalSpend().add(amount));
        if (occurredAt != null) {
            record.setLastOrderAt(occurredAt);
        }
        CustomerSpend saved = customerSpendRepository.save(record);
        log.info("Updated lifetime spend for userId={} -> {}", userId, saved.getTotalSpend());
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerSpend> findSpend(String userId) {
        return customerSpendRepository.findById(userId);
    }
}
