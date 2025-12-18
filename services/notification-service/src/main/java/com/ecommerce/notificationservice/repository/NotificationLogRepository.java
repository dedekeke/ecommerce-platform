package com.ecommerce.notificationservice.repository;

import com.ecommerce.notificationservice.domain.NotificationLog;
import com.ecommerce.notificationservice.domain.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {

    Page<NotificationLog> findByUserId(String userId, Pageable pageable);

    Page<NotificationLog> findByStatus(NotificationStatus status, Pageable pageable);

    List<NotificationLog> findByStatusAndNextRetryAtBefore(NotificationStatus status, Instant before);

    Page<NotificationLog> findByRelatedEntityIdAndRelatedEntityType(
            String relatedEntityId,
            String relatedEntityType,
            Pageable pageable
    );

    long countByStatusAndCreatedAtAfter(NotificationStatus status, Instant after);
}
