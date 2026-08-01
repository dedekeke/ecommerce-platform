package com.ecommerce.paymentservice.savedmethod;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Tokenized payment instrument owned by a user (§3.9).
 *
 * <p>Sensitive PAN data is never stored — only the upstream {@code providerId}
 * token plus display-only fields ({@code last4}, {@code brand},
 * {@code expMonth}, {@code expYear}).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "saved_payment_methods",
    indexes = {
        @Index(name = "idx_saved_payment_user", columnList = "userId")
    },
    uniqueConstraints = {
        // One row per (user, tokenized method): lets confirm + webhook converge idempotently.
        @UniqueConstraint(name = "uq_saved_payment_user_provider", columnNames = {"userId", "providerId"})
    })
public class SavedPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 128)
    private String userId;

    @NotBlank
    @Column(nullable = false, length = 32)
    private String provider;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String providerId;

    @Column(length = 4)
    private String last4;

    @Column(length = 32)
    private String brand;

    private Integer expMonth;

    private Integer expYear;

    @NotNull
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
