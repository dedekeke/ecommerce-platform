package com.ecommerce.userservice.dto;

import com.ecommerce.userservice.domain.User;
import com.ecommerce.userservice.domain.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User Profile Response DTO
 *
 * Represents user profile data returned to clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    private String id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String phoneNumber;
    private UserRole role;
    private Boolean active;
    private Boolean emailVerified;
    private String pictureUrl;
    private Instant lastLoginAt;
    private Instant createdAt;

    /**
     * Create from User entity
     */
    public static UserProfileResponse from(User user) {
        return UserProfileResponse.builder()
                .id(user.getId() != null ? user.getId().toString() : null)
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .active(user.getActive())
                .emailVerified(user.getEmailVerified())
                .pictureUrl(user.getPictureUrl())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }

}
