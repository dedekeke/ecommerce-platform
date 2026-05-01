package com.ecommerce.userservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Wishlist Entity
 *
 * Represents a single product entry on a user's wishlist. The (userId, productId)
 * pair is unique — a product can only appear once per user.
 *
 * <p>This entity stores {@code userId} as a numeric foreign key into {@code users.id}
 * (not the Auth0 sub claim) to keep the relational model clean. The controller
 * layer maps from Auth0 sub to user id.
 */
@Entity
@Table(name = "wishlists",
        uniqueConstraints = @UniqueConstraint(name = "uk_wishlists_user_product",
                columnNames = {"user_id", "product_id"}),
        indexes = {
                @Index(name = "idx_wishlists_user_id", columnList = "user_id"),
                @Index(name = "idx_wishlists_product_id", columnList = "product_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    @NotNull(message = "User id is required")
    private Long userId;

    @Column(name = "product_id", nullable = false, length = 100)
    @NotBlank(message = "Product id is required")
    @Size(max = 100, message = "Product id must not exceed 100 characters")
    private String productId;

    @Column(name = "added_at", nullable = false)
    @NotNull(message = "Added timestamp is required")
    private Instant addedAt;
}
