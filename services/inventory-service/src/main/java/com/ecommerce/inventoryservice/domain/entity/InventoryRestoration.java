package com.ecommerce.inventoryservice.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Idempotency ledger for {@code RestoreStock} gRPC calls.
 *
 * <p>The refund saga generates a {@code restorationId} per attempt and may
 * re-issue the same call after a recovery. Persisting the id here lets the
 * server detect the replay and skip the second increment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_restorations", indexes = {
    @Index(name = "idx_restoration_order_id", columnList = "orderId")
})
public class InventoryRestoration {

    @Id
    @Column(name = "restoration_id", nullable = false, length = 64)
    @NotBlank
    private String restorationId;

    @Column(name = "order_id", nullable = false, length = 64)
    @NotBlank
    private String orderId;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "items_count", nullable = false)
    private Integer itemsCount;

    @CreationTimestamp
    @Column(name = "restored_at", nullable = false, updatable = false)
    private LocalDateTime restoredAt;
}
