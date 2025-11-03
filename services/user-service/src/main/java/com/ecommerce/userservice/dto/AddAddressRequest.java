package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.domain.Address;
import com.ecommerce.userservice.domain.UserAddress;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Add Address Request DTO
 *
 * Request to add a new address for a user.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddAddressRequest {

    @NotBlank(message = "Address label is required")
    @Size(max = 50, message = "Label must not exceed 50 characters")
    private String label;

    @Valid
    @NotBlank(message = "Street address is required")
    private String street;

    private String addressLine2;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "State is required")
    private String state;

    @NotBlank(message = "Postal code is required")
    private String postalCode;

    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 2, message = "Country code must be 2 characters")
    private String country;

    @Builder.Default
    private Boolean isDefault = false;

    @Builder.Default
    private Boolean isBilling = false;

    private String recipientName;

    private String recipientPhone;

    /**
     * Convert to UserAddress entity
     */
    public UserAddress toEntity() {
        Address address = Address.builder()
                .street(street)
                .addressLine2(addressLine2)
                .city(city)
                .state(state)
                .postalCode(postalCode)
                .country(country)
                .build();

        return UserAddress.builder()
                .label(label)
                .address(address)
                .isDefault(isDefault != null ? isDefault : false)
                .isBilling(isBilling != null ? isBilling : false)
                .recipientName(recipientName)
                .recipientPhone(recipientPhone)
                .build();
    }

}
