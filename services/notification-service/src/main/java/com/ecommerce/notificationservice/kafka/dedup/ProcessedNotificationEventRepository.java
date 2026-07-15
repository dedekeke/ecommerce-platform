package com.ecommerce.notificationservice.kafka.dedup;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedNotificationEventRepository
        extends MongoRepository<ProcessedNotificationEvent, String> {
}
