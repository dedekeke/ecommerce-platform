package com.ecommerce.userservice.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * User Entity
 *
 * Represents a user in the e-commerce platform.
 * Synchronized with Auth0 user data on first login.
 *
 * Key Features:
 * - Auth0 integration via auth0Id (sub claim from JWT)
 * - Profile management (name, email, phone)
 * - Multiple addresses support with default selection
 * - Role-based access control
 * - Audit fields (createdAt, updatedAt)
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_auth0_id", columnList = "auth0_id", unique = true),
    @Index(name = "idx_email", columnList = "email", unique = true)
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Auth0 User ID (sub claim from JWT)
     * Format: "auth0|..." or "google-oauth2|..." depending on identity provider
     */
    @Column(name = "auth0_id", nullable = false, unique = true, length = 100)
    @NotBlank(message = "Auth0 ID is required")
    private String auth0Id;

    @Column(nullable = false, unique = true, length = 255)
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    @Column(name = "first_name", length = 100)
    @Size(max = 100, message = "First name must not exceed 100 characters")
    private String firstName;

    @Column(name = "last_name", length = 100)
    @Size(max = 100, message = "Last name must not exceed 100 characters")
    private String lastName;

    @Column(name = "phone_number", length = 20)
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$",
             message = "Phone number must be in E.164 format (e.g., +12125551234)")
    private String phoneNumber;

    /**
     * User role from Auth0 (e.g., "USER", "ADMIN")
     * Can be extracted from Auth0 app_metadata or roles claim
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserRole role = UserRole.USER;

    /**
     * User's addresses (shipping, billing, etc.)
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<UserAddress> addresses = new ArrayList<>();

    /**
     * Whether the user account is active
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Whether the user has verified their email
     * Synced from Auth0 email_verified claim
     */
    @Column(name = "email_verified")
    private Boolean emailVerified;

    /**
     * User's profile picture URL from Auth0
     */
    @Column(name = "picture_url", length = 500)
    private String pictureUrl;

    /**
     * Last login timestamp (updated on each login)
     */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Helper method to get full name
     */
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return email;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    /**
     * Helper method to add an address
     */
    public void addAddress(UserAddress address) {
        addresses.add(address);
        address.setUser(this);
    }

    /**
     * Helper method to remove an address
     */
    public void removeAddress(UserAddress address) {
        addresses.remove(address);
        address.setUser(null);
    }

    /**
     * Get the default address for shipping
     */
    public UserAddress getDefaultAddress() {
        return addresses.stream()
                .filter(UserAddress::getIsDefault)
                .findFirst()
                .orElse(null);
    }

    /**
     * Update last login timestamp to now
     */
    public void updateLastLogin() {
        this.lastLoginAt = Instant.now();
    }

}
