package com.ecommerce.inventoryservice.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Outbox row. One JPA entity per service (no shared module) — the table
 * lives inside each service's database so writing it joins the same
 * transaction as the aggregate write, giving us atomic dual-write
 * semantics without 2PC.
 *
 * <p>Lifecycle: created with {@code publishedAt = null} inside the same
 * {@code @Transactional} that mutates the aggregate. The {@link OutboxRelay}
 * polls unpublished rows, ships them to Kafka, then stamps {@code publishedAt}.
 */
@Entity
@Table(name = "outbox_event", indexes = {
    @Index(name = "idx_outbox_unpublished", columnList = "publishedAt, createdAt"),
    @Index(name = "idx_outbox_aggregate", columnList = "aggregateType, aggregateId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Stable UUID surfaced to consumers as the {@code outbox-event-id} Kafka
     * header. Consumers dedupe on this value to tolerate at-least-once delivery.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(nullable = false, length = 64)
    private String aggregateType;

    @Column(nullable = false, length = 128)
    private String aggregateId;

    @Column(nullable = false, length = 128)
    private String eventType;

    @Column(nullable = false, length = 128)
    private String topic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Stamped non-null when the relay has successfully sent the event to Kafka.
     * The unique index on {@code (publishedAt, createdAt)} makes "claim next
     * batch" cheap.
     */
    private LocalDateTime publishedAt;

    /**
     * Tracks publish retries so the relay can apply exponential backoff and
     * surface stuck events to operators via the {@code last_error} column.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(length = 1024)
    private String lastError;

    public boolean isPublished() {
        return publishedAt != null;
    }
}
