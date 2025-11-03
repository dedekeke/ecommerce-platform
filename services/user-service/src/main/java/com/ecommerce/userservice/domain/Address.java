package com.ecommerce.userservice.domain;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Address Embeddable Entity
 *
 * Represents a physical address that can be embedded in other entities.
 * Used for both shipping and billing addresses.
 */
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @NotBlank(message = "Street address is required")
    @Size(max = 255, message = "Street address must not exceed 255 characters")
    private String street;

    @Size(max = 100, message = "Address line 2 must not exceed 100 characters")
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;

    @NotBlank(message = "State/Province is required")
    @Size(max = 100, message = "State/Province must not exceed 100 characters")
    private String state;

    @NotBlank(message = "Postal code is required")
    @Pattern(regexp = "^[0-9]{5}(-[0-9]{4})?$|^[A-Z0-9]{3,10}$",
             message = "Invalid postal code format")
    private String postalCode;

    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 2, message = "Country code must be exactly 2 characters (ISO 3166-1 alpha-2)")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Country code must be uppercase letters")
    private String country;

    /**
     * Returns the full address as a formatted string
     */
    public String getFullAddress() {
        StringBuilder sb = new StringBuilder();
        sb.append(street);

        if (addressLine2 != null && !addressLine2.isBlank()) {
            sb.append(", ").append(addressLine2);
        }

        sb.append(", ").append(city)
          .append(", ").append(state)
          .append(" ").append(postalCode)
          .append(", ").append(country);

        return sb.toString();
    }

}
