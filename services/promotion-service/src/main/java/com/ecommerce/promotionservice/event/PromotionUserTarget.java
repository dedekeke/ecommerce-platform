package com.ecommerce.promotionservice.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Maps a promotion code to a targeted user (Auth0 {@code sub}).
 *
 * <p>Drives per-user promotion announcements: a promotion with at least one
 * target is announced only to those users; one with none falls back to the
 * broadcast recipient.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "promotion_user_targets",
        uniqueConstraints = @UniqueConstraint(name = "uq_promo_user", columnNames = {"promo_code", "user_id"}))
public class PromotionUserTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "promo_code", nullable = false, length = 50)
    private String promoCode;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
