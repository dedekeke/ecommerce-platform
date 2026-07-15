package com.ecommerce.userservice.exception;

/**
 * Email Already Registered Exception
 *
 * Thrown when a user provisioning attempt (e.g. first-login sync from Auth0)
 * finds an existing account with the same email but a different Auth0 id.
 *
 * Identity in this service is keyed on {@code auth0Id}; {@code email} is a
 * distinct unique column synced from Auth0. When those two disagree we refuse
 * to silently link the incoming Auth0 identity to the existing record — doing
 * so on an unverified email would be an account-takeover vector. Instead we
 * surface a controlled 409 CONFLICT so the collision can be resolved
 * deliberately, rather than letting the DB unique constraint blow up into an
 * opaque 500.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }

    public EmailAlreadyRegisteredException(String message, Throwable cause) {
        super(message, cause);
    }

    public static EmailAlreadyRegisteredException forEmail(String email) {
        return new EmailAlreadyRegisteredException(
                "An account already exists for email: " + email
        );
    }

}
