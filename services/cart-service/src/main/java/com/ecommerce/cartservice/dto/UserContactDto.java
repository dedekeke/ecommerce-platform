package com.ecommerce.cartservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subset of user-service's profile response used to resolve a shopper's
 * email by Auth0 sub for cart-abandonment notifications.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserContactDto {
    private String id;
    private String email;
    private String fullName;
}
