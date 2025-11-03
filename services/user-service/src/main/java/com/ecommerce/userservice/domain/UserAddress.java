package com.ecommerce.userservice.domain;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * User Address Entity
 *
 * Represents a saved address for a user.
 * Users can have multiple addresses (home, work, etc.)
 * with one marked as default for shipping.
 */
@Entity
@Table(name = "user_addresses", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_user_default", columnList = "user_id,is_default")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Label for this address (e.g., "Home", "Work", "Office")
     */
    @Column(nullable = false, length = 50)
    @NotBlank(message = "Address label is required")
    @Size(max = 50, message = "Label must not exceed 50 characters")
    private String label;

    /**
     * The actual address details
     */
    @Embedded
    @Valid
    private Address address;

    /**
     * Whether this is the user's default shipping address
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;

    /**
     * Whether this is a billing address
     */
    @Column(name = "is_billing", nullable = false)
    @Builder.Default
    private Boolean isBilling = false;

    /**
     * Optional recipient name (if different from user)
     */
    @Column(name = "recipient_name", length = 200)
    @Size(max = 200, message = "Recipient name must not exceed 200 characters")
    private String recipientName;

    /**
     * Optional phone number for delivery (if different from user)
     */
    @Column(name = "recipient_phone", length = 20)
    private String recipientPhone;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Helper method to get full address string
     */
    public String getFullAddress() {
        if (address != null) {
            return address.getFullAddress();
        }
        return "";
    }

}
