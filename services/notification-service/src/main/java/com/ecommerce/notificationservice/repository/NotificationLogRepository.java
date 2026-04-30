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

    List<NotificationLog> findByRelatedEntityIdAndRelatedEntityType(
            String relatedEntityId,
            String relatedEntityType
    );

    long countByStatusAndCreatedAtAfter(NotificationStatus status, Instant after);

    /**
     * Idempotency check: returns true when a notification for the given entity + template has
     * already been delivered (SENT) or is pending delivery (PENDING / RETRYING). Used by Kafka
     * consumers to skip duplicate events produced by at-least-once delivery or replay attacks.
     */
    boolean existsByRelatedEntityIdAndTemplateCodeAndStatusIn(
            String relatedEntityId,
            String templateCode,
            List<NotificationStatus> statuses
    );
}
