package com.ecommerce.promotionservice.saga.replenishment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConsumedReplenishmentEventRepository
        extends JpaRepository<ConsumedReplenishmentEvent, UUID> {
}
