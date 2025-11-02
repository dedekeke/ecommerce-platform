package com.ecommerce.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Event published when a new user is created.
 * Triggers welcome email and initial setup tasks.
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserCreatedEvent extends BaseEvent {

    private static final long serialVersionUID = 1L;

    /**
     * User identifier (internal)
     */
    private String internalUserId;

    /**
     * Auth0 user identifier
     */
    private String auth0Id;

    /**
     * Email address
     */
    private String email;

    /**
     * First name
     */
    private String firstName;

    /**
     * Last name
     */
    private String lastName;

    /**
     * Phone number
     */
    private String phoneNumber;

    /**
     * User role (USER, ADMIN, etc.)
     */
    private String role;

    /**
     * Whether email is verified
     */
    private Boolean emailVerified;

    @Override
    public String getPartitionKey() {
        return auth0Id;
    }
}
