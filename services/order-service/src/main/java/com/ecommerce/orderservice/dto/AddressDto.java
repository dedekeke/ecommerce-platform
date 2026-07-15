package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.domain.embedded.Address;
import jakarta.validation.constraints.NotBlank;

/** Shipping address supplied by the checkout client. */
public record AddressDto(
    @NotBlank(message = "Street is required") String street,
    @NotBlank(message = "City is required") String city,
    @NotBlank(message = "State is required") String state,
    @NotBlank(message = "Postal code is required") String postalCode,
    @NotBlank(message = "Country is required") String country
) {
    public Address toDomain() {
        return Address.builder()
            .street(street)
            .city(city)
            .state(state)
            .postalCode(postalCode)
            .country(country)
            .build();
    }
}
