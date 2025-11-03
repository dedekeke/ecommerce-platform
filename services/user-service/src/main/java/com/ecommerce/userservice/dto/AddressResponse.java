package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.domain.Address;
import com.ecommerce.userservice.domain.UserAddress;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Address Response DTO
 *
 * Represents an address returned to clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {

    private Long id;
    private String label;
    private String street;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String fullAddress;
    private Boolean isDefault;
    private Boolean isBilling;
    private String recipientName;
    private String recipientPhone;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * Create from UserAddress entity
     */
    public static AddressResponse from(UserAddress userAddress) {
        Address address = userAddress.getAddress();

        return AddressResponse.builder()
                .id(userAddress.getId())
                .label(userAddress.getLabel())
                .street(address != null ? address.getStreet() : null)
                .addressLine2(address != null ? address.getAddressLine2() : null)
                .city(address != null ? address.getCity() : null)
                .state(address != null ? address.getState() : null)
                .postalCode(address != null ? address.getPostalCode() : null)
                .country(address != null ? address.getCountry() : null)
                .fullAddress(userAddress.getFullAddress())
                .isDefault(userAddress.getIsDefault())
                .isBilling(userAddress.getIsBilling())
                .recipientName(userAddress.getRecipientName())
                .recipientPhone(userAddress.getRecipientPhone())
                .createdAt(userAddress.getCreatedAt())
                .updatedAt(userAddress.getUpdatedAt())
                .build();
    }

}
