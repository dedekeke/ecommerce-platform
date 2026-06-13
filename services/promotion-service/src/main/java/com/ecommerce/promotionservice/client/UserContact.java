package com.ecommerce.promotionservice.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal user contact info resolved from user-service for promotion
 * targeting. Only the fields the announcement email needs are mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserContact(String id, String email, String fullName) {
}
