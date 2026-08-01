package com.ecommerce.notificationservice.saga.replenishment;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConsumedReplenishmentEventRepository
        extends MongoRepository<ConsumedReplenishmentEvent, UUID> {
}
