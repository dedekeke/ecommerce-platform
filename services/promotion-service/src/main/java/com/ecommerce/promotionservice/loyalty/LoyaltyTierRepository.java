package com.ecommerce.promotionservice.loyalty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoyaltyTierRepository extends JpaRepository<LoyaltyTier, Long> {

    /** Tiers ordered ascending by minSpend (BRONZE → PLATINUM). */
    List<LoyaltyTier> findAllByOrderByMinSpendAsc();

    Optional<LoyaltyTier> findByName(String name);
}
