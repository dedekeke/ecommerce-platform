package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.domain.User;

/**
 * Minimal contact projection for the internal service-to-service lookup
 * ({@code GET /api/users/by-auth0/{sub}}).
 *
 * <p>Deliberately exposes only the email and display name needed by
 * promotion-service (targeted promotion emails) and cart-service (abandoned-cart
 * reminders). It does NOT expose role, active/verified flags, phone number,
 * picture, timestamps or the internal id — returning the full
 * {@link UserProfileResponse} here would turn this endpoint into a PII
 * enumeration oracle for any authenticated caller.
 */
public record UserContactResponse(String email, String fullName) {

    public static UserContactResponse from(User user) {
        return new UserContactResponse(user.getEmail(), user.getFullName());
    }
}
