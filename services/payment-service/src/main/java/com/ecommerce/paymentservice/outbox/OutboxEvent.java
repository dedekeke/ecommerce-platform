package com.ecommerce.paymentservice.outbox;

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
 * Outbox row for the payment-service. See order-service's {@code OutboxEvent}
 * for the full pattern documentation; payment-service has its own copy
 * because the table physically lives inside the payment-service database.
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

    private LocalDateTime publishedAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(length = 1024)
    private String lastError;

    public boolean isPublished() {
        return publishedAt != null;
    }
}
